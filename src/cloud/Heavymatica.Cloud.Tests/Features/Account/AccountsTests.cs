using Heavymatica.Cloud.Features.Account;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Account;

[Trait("Category", "Integration")]
public sealed class AccountsTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly CloudFactory factory = new(infrastructure) { Clock = new FakeTimeProvider(new DateTimeOffset(2026, 10, 23, 12, 0, 0, TimeSpan.Zero)) };

    public void Dispose() => factory.Dispose();

    private Accounts Accounts => factory.Services.GetRequiredService<Accounts>();

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task Creating_an_account_links_the_identity_under_an_id_not_derived_from_it()
    {
        var (signedIn, created) = await Accounts.CreateWithIdentityAsync(Providers.GitHub, "583231", "octocat", Cancel);

        Assert.True(created);
        Assert.Matches("^[0-9a-f]{32}$", signedIn.Account.Id);
        Assert.DoesNotContain("583231", signedIn.Account.Id, StringComparison.Ordinal);
        Assert.Equal((signedIn.Account.Id, "github", "583231", "octocat"),
            (signedIn.Identity.AccountId, signedIn.Identity.Provider, signedIn.Identity.Subject, signedIn.Identity.Name));
        Assert.Equal(0, signedIn.Account.UsedBytes);
        Assert.Equal(100L * 1024 * 1024, signedIn.Account.QuotaBytes);
    }

    [Fact]
    public async Task An_account_is_found_by_its_identity()
    {
        var (created, _) = await Accounts.CreateWithIdentityAsync(Providers.GitHub, "1001", "finder", Cancel);

        var found = await Accounts.FindByIdentityAsync(Providers.GitHub, "1001", Cancel);
        var other = await Accounts.FindByIdentityAsync(Providers.Minecraft, "1001", Cancel);

        Assert.Equal(created.Account.Id, found?.Account.Id);
        Assert.Equal(created.Identity.Id, found?.Identity.Id);
        Assert.Null(other);
    }

    [Fact]
    public async Task Concurrent_creates_of_one_identity_make_exactly_one_account()
    {
        var results = await Task.WhenAll(Enumerable.Range(0, 8).Select(_ =>
            Task.Run(() => Accounts.CreateWithIdentityAsync(Providers.GitHub, "4242", "racer", Cancel), Cancel)));

        Assert.Single(results.Select(result => result.SignedIn.Account.Id).Distinct());
        Assert.Single(results, result => result.Created);
        Assert.Equal(1, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
        Assert.Equal(1, await IdentityDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task An_identity_taken_by_another_account_is_rejected_and_nothing_changes()
    {
        var (first, _) = await Accounts.CreateWithIdentityAsync(Providers.GitHub, "7", "first", Cancel);
        var (second, _) = await Accounts.CreateWithIdentityAsync(Providers.GitHub, "8", "second", Cancel);

        var outcome = await Accounts.AddIdentityAsync(second.Account.Id, Providers.GitHub, "7", "first", second.Actor, Cancel);

        Assert.Equal(AddIdentityOutcome.AlreadyLinked, outcome);
        Assert.Equal(first.Account.Id, (await Accounts.FindByIdentityAsync(Providers.GitHub, "7", Cancel))?.Account.Id);
        Assert.Equal(1, await IdentityDocument.In(factory.Database).CountDocumentsAsync(identity => identity.AccountId == second.Account.Id,
            cancellationToken: Cancel));
    }

    [Fact]
    public async Task The_eleventh_identity_is_rejected()
    {
        var (signedIn, _) = await Accounts.CreateWithIdentityAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Main", Cancel);
        for (var i = 0; i < 9; i++)
        {
            Assert.Equal(AddIdentityOutcome.Added,
                await Accounts.AddIdentityAsync(signedIn.Account.Id, Providers.Minecraft, Guid.NewGuid().ToString(), "Alt" + i, signedIn.Actor, Cancel));
        }

        var outcome = await Accounts.AddIdentityAsync(signedIn.Account.Id, Providers.GitHub, "11", "eleventh", signedIn.Actor, Cancel);

        Assert.Equal(AddIdentityOutcome.TooManyIdentities, outcome);
        Assert.Equal(10, await IdentityDocument.In(factory.Database).CountDocumentsAsync(identity => identity.AccountId == signedIn.Account.Id,
            cancellationToken: Cancel));
        Assert.Null(await Accounts.FindByIdentityAsync(Providers.GitHub, "11", Cancel));
    }

    [Fact]
    public async Task Creating_an_account_writes_one_audit_entry_with_time_account_and_acting_identity()
    {
        var (signedIn, _) = await Accounts.CreateWithIdentityAsync(Providers.GitHub, "583231", "octocat", Cancel);

        var entries = await AuditEntryDocument.In(factory.Database).Find(_ => true).ToListAsync(Cancel);

        var entry = Assert.Single(entries);
        Assert.Equal(AuditKinds.AccountCreated, entry.Kind);
        Assert.Equal(signedIn.Account.Id, entry.AccountId);
        Assert.Equal(new AuditActor("github", signedIn.Identity.Id), entry.Actor);
        Assert.Equal(new DateTime(2026, 10, 23, 12, 0, 0, DateTimeKind.Utc), entry.At);
    }

    [Fact]
    public async Task Adding_and_removing_identities_is_audited()
    {
        var (signedIn, _) = await Accounts.CreateWithIdentityAsync(Providers.GitHub, "1", "main", Cancel);
        await Accounts.AddIdentityAsync(signedIn.Account.Id, Providers.Minecraft, Guid.NewGuid().ToString(), "Alt", signedIn.Actor, Cancel);
        var alt = await IdentityDocument.In(factory.Database).Find(identity => identity.Provider == Providers.Minecraft).SingleAsync(Cancel);

        Assert.True(await Accounts.RemoveIdentityAsync(signedIn.Account.Id, alt.Id, signedIn.Actor, Cancel));

        var kinds = await AuditEntryDocument.In(factory.Database).Find(entry => entry.AccountId == signedIn.Account.Id)
            .SortBy(entry => entry.Id).Project(entry => new { entry.Kind, entry.IdentityId }).ToListAsync(Cancel);
        Assert.Equal(
            [new { Kind = AuditKinds.AccountCreated, IdentityId = (string?)null }, new { Kind = AuditKinds.IdentityAdded, IdentityId = (string?)alt.Id },
                new { Kind = AuditKinds.IdentityRemoved, IdentityId = (string?)alt.Id }],
            kinds);
    }

    [Fact]
    public async Task Startup_creates_the_identity_and_audit_indexes()
    {
        _ = Accounts;

        using var identities = await factory.Database.GetCollection<BsonDocument>(IdentityDocument.Collection).Indexes.ListAsync(Cancel);
        var byName = (await identities.ToListAsync(Cancel)).ToDictionary(index => index["name"].AsString);
        using var audit = await factory.Database.GetCollection<BsonDocument>(AuditEntryDocument.Collection).Indexes.ListAsync(Cancel);

        Assert.True(byName["provider_subject"]["unique"].AsBoolean);
        Assert.True(byName.ContainsKey("accountId"));
        Assert.Contains(await audit.ToListAsync(Cancel), index => index["name"] == "accountId_at");
    }
}

using System.Net;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Admin;
using Heavymatica.Cloud.Tests.Features.Schematics;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminAccountsTests(CloudInfrastructure infrastructure) : IDisposable
{
    private const long MiB = 1024 * 1024;

    private AdminSite? site;
    private AdminBrowser? admin;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    private Accounts Accounts => Site.Factory.Services.GetRequiredService<Accounts>();

    public void Dispose() => site?.Dispose();

    private async Task<AdminBrowser> AdminAsync() => admin ??= (await Site.AdminAsync()).Browser;

    private async Task<AccountSearch> FindAsync(string q)
    {
        using var response = await (await AdminAsync()).GetAsync("/admin/v1/accounts?q=" + Uri.EscapeDataString(q));
        return await AdminBrowser.ReadAsync<AccountSearch>(response);
    }

    private async Task<SignedInIdentity> PlayerAsync(string provider, string subject, string name) =>
        (await Accounts.CreateWithIdentityAsync(provider, subject, name, Cancel)).SignedIn;

    [Fact]
    public async Task Accounts_are_found_by_minecraft_name_in_any_letter_case()
    {
        var steve = await PlayerAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Steve");
        var other = await PlayerAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Steven");

        var found = await FindAsync("steve");

        var account = Assert.Single(found.Accounts);
        Assert.Equal(steve.Account.Id, account.Id);
        Assert.Equal([("minecraft", steve.Identity.Subject, "Steve")], account.Identities.Select(identity => (identity.Provider, identity.Subject, identity.Name)));
        Assert.Equal((0L, 100 * MiB), (account.UsedBytes, account.QuotaBytes));
        Assert.NotEqual(other.Account.Id, account.Id);
    }

    [Fact]
    public async Task Accounts_are_found_by_github_login_account_id_and_minecraft_uuid()
    {
        var github = await PlayerAsync(Providers.GitHub, "4711", "Octo-Cat");
        var uuid = Guid.NewGuid();
        var minecraft = await PlayerAsync(Providers.Minecraft, uuid.ToString(), "Alex");

        Assert.Equal([github.Account.Id], (await FindAsync("octo-cat")).Accounts.Select(account => account.Id));
        Assert.Equal([github.Account.Id], (await FindAsync(github.Account.Id.ToUpperInvariant())).Accounts.Select(account => account.Id));
        Assert.Equal([minecraft.Account.Id], (await FindAsync(uuid.ToString().ToUpperInvariant())).Accounts.Select(account => account.Id));
        Assert.Equal([minecraft.Account.Id], (await FindAsync(uuid.ToString("N"))).Accounts.Select(account => account.Id));
    }

    [Fact]
    public async Task A_name_no_identity_has_finds_nothing()
    {
        _ = await PlayerAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Steve");

        Assert.Empty((await FindAsync("herobrine")).Accounts);
        Assert.Empty((await FindAsync(AccountDocument.NewId())).Accounts);
        using var empty = await (await AdminAsync()).GetAsync("/admin/v1/accounts?q=");
        Assert.Equal(HttpStatusCode.BadRequest, empty.StatusCode);
    }

    [Fact]
    public async Task A_name_search_returns_at_most_50_accounts()
    {
        var identities = Enumerable.Range(0, 55).Select(i => Cloud.Features.Account.Accounts.NewIdentity(AccountDocument.NewId(), Providers.Minecraft,
            Guid.NewGuid().ToString(), "Steve", DateTime.UtcNow)).ToList();
        await AccountDocument.In(Site.Factory.Database).InsertManyAsync(identities.Select(identity => new AccountDocument
        {
            Id = identity.AccountId,
            QuotaBytes = 100 * MiB,
            CreatedAt = DateTimeOffset.UtcNow,
        }), cancellationToken: Cancel);
        await IdentityDocument.In(Site.Factory.Database).InsertManyAsync(identities, cancellationToken: Cancel);

        Assert.Equal(50, (await FindAsync("STEVE")).Accounts.Count);
    }

    [Fact]
    public async Task Names_are_stored_in_lower_case_on_sign_in_and_rename_and_backfilled_for_older_identities()
    {
        var subject = Guid.NewGuid().ToString();
        _ = await PlayerAsync(Providers.Minecraft, subject, "Steve");
        _ = await Accounts.SignInAsync(Providers.Minecraft, subject, "NotSteve", Cancel);
        var older = Cloud.Features.Account.Accounts.NewIdentity(AccountDocument.NewId(), Providers.GitHub, "99", "Old-Login", DateTime.UtcNow);
        older.NameLower = null;
        var identities = IdentityDocument.In(Site.Factory.Database);
        await identities.InsertOneAsync(older, cancellationToken: Cancel);
        await identities.UpdateOneAsync(identity => identity.Id == older.Id, Builders<IdentityDocument>.Update.Unset(identity => identity.NameLower),
            cancellationToken: Cancel);

        await new NameBackfill(Site.Factory.Database, NullLogger<NameBackfill>.Instance).StartAsync(Cancel);
        await new NameBackfill(Site.Factory.Database, NullLogger<NameBackfill>.Instance).StartAsync(Cancel);

        Assert.Equal("notsteve", (await identities.Find(identity => identity.Subject == subject).SingleAsync(Cancel)).NameLower);
        Assert.Equal("old-login", (await identities.Find(identity => identity.Id == older.Id).SingleAsync(Cancel)).NameLower);
        var indexes = await (await identities.Indexes.ListAsync(Cancel)).ToListAsync(Cancel);
        Assert.Contains(indexes, index => index["name"] == "nameLower");
    }

    [Fact]
    public async Task The_overview_lists_identities_usage_quota_schematic_count_and_audit_but_no_schematic_content()
    {
        var steve = await PlayerAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Steve");
        Site.Clock.Advance(TimeSpan.FromSeconds(1));
        Assert.Equal(AddIdentityOutcome.Added, await Accounts.AddIdentityAsync(steve.Account.Id, Providers.Minecraft, Guid.NewGuid().ToString(), "Steve_Alt",
            steve.Actor, Cancel));
        await Site.Factory.Database.GetCollection<BsonDocument>("schematics").InsertManyAsync(
            Enumerable.Range(0, 12).Select(i => new BsonDocument { ["_id"] = AccountDocument.NewId(), ["owner"] = steve.Account.Id, ["name"] = $"House {i}" }),
            cancellationToken: Cancel);
        await AccountDocument.In(Site.Factory.Database).UpdateOneAsync(account => account.Id == steve.Account.Id,
            Builders<AccountDocument>.Update.Set(account => account.UsedBytes, 40 * MiB), cancellationToken: Cancel);

        using var response = await (await AdminAsync()).GetAsync("/admin/v1/accounts/" + steve.Account.Id);
        var body = await response.Content.ReadAsStringAsync(Cancel);
        var overview = await AdminBrowser.ReadAsync<AccountOverview>(response);

        Assert.Equal(["Steve", "Steve_Alt"], overview.Identities.Select(identity => identity.Name));
        Assert.Equal((12L, 40 * MiB, 100 * MiB), (overview.SchematicCount, overview.UsedBytes, overview.QuotaBytes));
        Assert.Equal([AuditKinds.IdentityAdded, AuditKinds.AccountCreated], overview.Audit.Select(entry => entry.Kind));
        Assert.Equal("minecraft:" + steve.Identity.Subject, overview.Audit[0].Actor);
        Assert.DoesNotContain("House", body, StringComparison.Ordinal);
        Assert.DoesNotContain("thumbnail", body, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task An_unknown_account_is_not_found()
    {
        using var response = await (await AdminAsync()).GetAsync("/admin/v1/accounts/" + AccountDocument.NewId());

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(ProblemCode.Not_found, (await response.AdminProblemAsync()).Code);
    }

    [Fact]
    public async Task A_raised_quota_is_reported_and_recorded_with_both_values_and_the_reason()
    {
        var steve = await PlayerAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Steve");
        var browser = await AdminAsync();

        using var response = await browser.SendAsync(HttpMethod.Put, $"/admin/v1/accounts/{steve.Account.Id}/quota",
            new { quotaBytes = 1024 * MiB, reason = "creator programme" });
        var overview = await AdminBrowser.ReadAsync<AccountOverview>(response);

        Assert.Equal(1024 * MiB, overview.QuotaBytes);
        Assert.Equal(1024 * MiB, (await Accounts.FindAsync(steve.Account.Id, Cancel))!.QuotaBytes);
        var entry = await AdminAuditDocument.In(Site.Factory.Database).Find(candidate => candidate.Kind == AdminAuditKinds.QuotaChanged).SingleAsync(Cancel);
        Assert.Equal(("104857600", "1073741824", "creator programme"), (entry.Details["oldBytes"], entry.Details["newBytes"], entry.Details["reason"]));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("  ok  ")]
    public async Task A_quota_change_without_a_reason_is_rejected_and_the_quota_is_unchanged(string? reason)
    {
        var steve = await PlayerAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Steve");

        using var response = await (await AdminAsync()).SendAsync(HttpMethod.Put, $"/admin/v1/accounts/{steve.Account.Id}/quota",
            new { quotaBytes = 1024 * MiB, reason });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(ProblemCode.Invalid_request, (await response.AdminProblemAsync()).Code);
        Assert.Equal(100 * MiB, (await Accounts.FindAsync(steve.Account.Id, Cancel))!.QuotaBytes);
        Assert.Equal(0, await AdminAuditDocument.In(Site.Factory.Database).CountDocumentsAsync(entry => entry.Kind == AdminAuditKinds.QuotaChanged,
            cancellationToken: Cancel));
    }

    [Theory]
    [InlineData(-1L)]
    [InlineData(100L * 1024 * MiB + 1)]
    public async Task A_quota_outside_0_to_100_GiB_is_rejected(long quotaBytes)
    {
        var steve = await PlayerAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Steve");

        using var response = await (await AdminAsync()).SendAsync(HttpMethod.Put, $"/admin/v1/accounts/{steve.Account.Id}/quota",
            new { quotaBytes, reason = "testing" });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task A_quota_below_usage_keeps_the_schematics_and_rejects_the_next_upload()
    {
        var client = Site.PublicClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Steve");
        client.Authorize(session.AccessToken);
        var library = new Library(client);
        var stored = await library.NewAsync("House", Library.Fixture("house.nbt"));
        var accountId = (await library.MeAsync()).Id;
        var before = (await library.ListAsync()).Items.Count;

        using var lowered = await (await AdminAsync()).SendAsync(HttpMethod.Put, $"/admin/v1/accounts/{accountId}/quota", new { quotaBytes = 10, reason = "abuse" });
        using var upload = await library.UploadNewAsync("Another", Library.Fixture("house.nbt"));

        Assert.Equal(HttpStatusCode.OK, lowered.StatusCode);
        Assert.Equal(HttpStatusCode.UnprocessableEntity, upload.StatusCode);
        Assert.Equal(Cloud.Contract.ProblemCode.Over_quota, (await upload.ProblemAsync()).Code);
        Assert.Equal(before, (await library.ListAsync()).Items.Count);
        Assert.Equal(stored.SchematicId, (await library.DetailsAsync(stored.SchematicId)).Id);
    }
}

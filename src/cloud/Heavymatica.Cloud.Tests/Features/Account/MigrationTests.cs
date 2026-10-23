using System.Collections.Concurrent;
using System.Net;
using System.Net.Http.Json;
using System.Security.Claims;
using Akka.Actor;
using Akka.Hosting;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Features.Schematics;
using Heavymatica.Cloud.Features.Schematics.Sweeper;
using Heavymatica.Cloud.Tests.Features.Schematics;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Account;

[Trait("Category", "Integration")]
public sealed class MigrationTests(CloudInfrastructure infrastructure) : IDisposable
{
    private const long MiB = 1024 * 1024;

    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task A_service_on_an_empty_database_skips_the_migration()
    {
        var factory = Track(new CloudFactory(infrastructure));
        using var client = factory.CreateClient();

        using var health = await client.GetAsync(new Uri("/health", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.OK, health.StatusCode);
        Assert.Equal(0, await MigrationDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_legacy_account_keeps_its_library_files_usage_quota_and_session()
    {
        var legacy = Track(new LegacyData(infrastructure, CloudFactory.NewDatabaseName()));
        var steve = await legacy.AccountAsync("Steve", [2, 2, 1], thumbnails: 2, quotaBytes: 200 * MiB, Cancel);
        var oldKeys = await legacy.KeysAsync($"accounts/{steve.Key}/", Cancel);
        var clock = new FakeTimeProvider(DateTimeOffset.UtcNow);
        var factory = Track(new CloudFactory(infrastructure, legacy.DatabaseName) { Clock = clock });

        using var client = factory.CreateClient();

        var account = Assert.Single(await AccountDocument.In(factory.Database).Find(_ => true).ToListAsync(Cancel));
        var identity = Assert.Single(await IdentityDocument.In(factory.Database).Find(_ => true).ToListAsync(Cancel));
        Assert.Matches("^[0-9a-f]{32}$", account.Id);
        Assert.Equal((account.Id, "minecraft", steve.Key, "Steve"), (identity.AccountId, identity.Provider, identity.Subject, identity.Name));
        Assert.Equal((steve.UsedBytes, 200 * MiB), (account.UsedBytes, account.QuotaBytes));
        var audit = Assert.Single(await AuditEntryDocument.In(factory.Database).Find(_ => true).ToListAsync(Cancel));
        Assert.Equal((AuditKinds.AccountMigrated, account.Id, new AuditActor("minecraft", identity.Id)), (audit.Kind, audit.AccountId, audit.Actor));

        // The stored refresh token renews into tokens for the new account.
        using var refreshed = await client.PostAsJsonAsync(new Uri("/v1/auth/refresh", UriKind.Relative), new { refreshToken = steve.RefreshToken }, Cancel);
        Assert.Equal(HttpStatusCode.OK, refreshed.StatusCode);
        var session = (await refreshed.Content.ReadFromJsonAsync<Session>(SignIn.Json, Cancel))!;
        Assert.Equal(account.Id, new Microsoft.IdentityModel.JsonWebTokens.JsonWebToken(session.AccessToken).Subject);
        Assert.Equal((account.Id, steve.Uuid), (session.Account.Id, session.Account.Uuid));

        // A fresh sign-in reaches the same account and sees the whole library.
        var signedIn = await SignIn.AsAsync(client, steve.Uuid, "Steve");
        Assert.Equal(account.Id, signedIn.Account.Id);
        var library = new Library(client.Authorize(signedIn.AccessToken));
        var me = await library.MeAsync();
        Assert.Equal((steve.UsedBytes, 200 * MiB), (me.UsedBytes, me.QuotaBytes));
        Assert.Equal(steve.SchematicIds.Order(), (await library.ListAsync()).Items.Select(item => item.Id).Order());
        var versions = 0;
        var thumbnails = 0;
        using var download = new HttpClient();
        foreach (var schematicId in steve.SchematicIds)
        {
            foreach (var version in (await library.DetailsAsync(schematicId)).Versions)
            {
                versions++;
                using var linksResponse = await library.LinksAsync(schematicId, version.Number);
                var links = (await linksResponse.Content.ReadFromJsonAsync<DownloadLinks>(SignIn.Json, Cancel))!;
                var file = await download.GetByteArrayAsync(links.FileUrl, Cancel);
                Assert.Equal(version.Sha256, Convert.ToHexStringLower(System.Security.Cryptography.SHA256.HashData(file)));
                Assert.Contains($"/accounts/{account.Id}/", links.FileUrl.ToString(), StringComparison.Ordinal);
                if (version.HasThumbnail)
                {
                    thumbnails++;
                    Assert.NotEmpty(await download.GetByteArrayAsync(links.ThumbnailUrl!, Cancel));
                }
            }
        }

        Assert.Equal((5, 2), (versions, thumbnails));

        // The sweeper removes the old copies and keeps the new ones.
        clock.Advance(TimeSpan.FromHours(2));
        var sweeper = factory.Services.GetRequiredService<IRequiredActor<BlobSweeperActor>>();
        var deleted = await sweeper.ActorRef.Ask<int>(new BlobSweeperActor.Sweep(), TimeSpan.FromSeconds(30), Cancel);
        Assert.Equal(oldKeys.Count, deleted);
        Assert.Empty(await legacy.KeysAsync($"accounts/{steve.Key}/", Cancel));
        Assert.Equal(oldKeys.Select(key => key[(key.IndexOf('/', 9) + 1)..]).Order(),
            (await legacy.KeysAsync($"accounts/{account.Id}/", Cancel)).Select(key => key[(key.IndexOf('/', 9) + 1)..]).Order());
    }

    [Fact]
    public async Task An_old_access_token_is_rejected_once_and_the_client_retry_after_refresh_succeeds()
    {
        var legacy = Track(new LegacyData(infrastructure, CloudFactory.NewDatabaseName()));
        var alex = await legacy.AccountAsync("Alex", [1, 1], thumbnails: 0, quotaBytes: 100 * MiB, Cancel);
        var factory = Track(new CloudFactory(infrastructure, legacy.DatabaseName));
        using var client = factory.CreateClient();
        var oldToken = factory.Services.GetRequiredService<AccessTokens>().Issue(
            [new Claim("sub", alex.Key), new Claim("name", "Alex"), new Claim("sid", alex.SessionId)]).Token;

        client.Authorize(oldToken);
        using var rejected = await client.GetAsync(new Uri("/v1/schematics", UriKind.Relative), Cancel);
        using var refreshed = await client.PostAsJsonAsync(new Uri("/v1/auth/refresh", UriKind.Relative), new { refreshToken = alex.RefreshToken }, Cancel);
        var session = (await refreshed.Content.ReadFromJsonAsync<Session>(SignIn.Json, Cancel))!;
        var page = await new Library(client.Authorize(session.AccessToken)).ListAsync();

        Assert.Equal(HttpStatusCode.Unauthorized, rejected.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await rejected.ProblemAsync()).Code);
        Assert.Equal(alex.SchematicIds.Order(), page.Items.Select(item => item.Id).Order());
    }

    [Fact]
    public async Task An_interrupted_migration_completes_on_the_next_start_exactly_once()
    {
        var legacy = Track(new LegacyData(infrastructure, CloudFactory.NewDatabaseName()));
        var accounts = new[]
        {
            await legacy.AccountAsync("First", [2, 1], thumbnails: 1, quotaBytes: 100 * MiB, Cancel),
            await legacy.AccountAsync("Second", [1, 2], thumbnails: 1, quotaBytes: 100 * MiB, Cancel),
        }.OrderBy(account => account.Key, StringComparer.Ordinal).ToArray();
        var failing = new FailAfter(accounts[1].Key, MigrationStep.FilesCopied);

        var crashed = new CloudFactory(infrastructure, legacy.DatabaseName) { TestServices = services => services.AddSingleton<IMigrationObserver>(failing) };
        Assert.ThrowsAny<Exception>(() => crashed.Services);
        DisposeCrashed(crashed);

        var midway = await MigrationDocument.In(legacy.Database).Find(migration => migration.Id != MigrationDocument.LockId).ToListAsync(Cancel);
        Assert.Equal([MigrationStep.Finished, MigrationStep.FilesCopied], midway.OrderBy(migration => migration.Id, StringComparer.Ordinal).Select(migration => migration.Step));

        var restarted = Track(new CloudFactory(infrastructure, legacy.DatabaseName));
        _ = restarted.Services;
        var snapshot = await SnapshotAsync(legacy);

        Assert.Equal(2, snapshot.Accounts.Count);
        Assert.Equal(2, snapshot.Identities.Count);
        Assert.Equal(accounts.Select(account => account.Key).Order(), snapshot.Identities.Select(identity => identity["subject"].AsString).Order());
        Assert.Equal(2, snapshot.Audit.Count(entry => entry["kind"] == AuditKinds.AccountMigrated));
        Assert.All(snapshot.Sessions, session => Assert.False(session["identityId"].IsBsonNull));
        Assert.Equal(accounts.Select(account => account.SessionId).Order(), snapshot.Sessions.Select(session => session["_id"].AsString).Order());
        foreach (var account in accounts)
        {
            var identity = snapshot.Identities.Single(candidate => candidate["subject"] == account.Key);
            var accountId = identity["accountId"].AsString;
            Assert.Equal(account.UsedBytes, snapshot.Accounts.Single(candidate => candidate["_id"] == accountId)["usedBytes"].ToInt64());
            Assert.Equal(account.Versions, await VersionDocument.In(legacy.Database).CountDocumentsAsync(version => version.Owner == accountId, cancellationToken: Cancel));
            var oldKeys = await legacy.KeysAsync($"accounts/{account.Key}/", Cancel);
            var newKeys = await legacy.KeysAsync($"accounts/{accountId}/", Cancel);
            Assert.Equal(oldKeys.Count, newKeys.Count);
        }

        Assert.Equal(0, await SchematicDocument.In(legacy.Database).CountDocumentsAsync(
            schematic => schematic.Owner == accounts[0].Key || schematic.Owner == accounts[1].Key || schematic.Latest.Owner == accounts[1].Key,
            cancellationToken: Cancel));

        var again = Track(new CloudFactory(infrastructure, legacy.DatabaseName));
        _ = again.Services;
        var after = await SnapshotAsync(legacy);
        Assert.Equal(snapshot.Accounts, after.Accounts);
        Assert.Equal(snapshot.Identities, after.Identities);
        Assert.Equal(snapshot.Audit, after.Audit);
        Assert.Equal(snapshot.Sessions, after.Sessions);
        Assert.Equal(snapshot.Schematics, after.Schematics);
        Assert.Equal(snapshot.Versions, after.Versions);
        Assert.Equal(snapshot.Migrations, after.Migrations);
        Assert.Equal(snapshot.Blobs, after.Blobs);
    }

    [Fact]
    public async Task Of_two_instances_starting_together_only_one_migrates_and_the_other_starts_afterwards()
    {
        var legacy = Track(new LegacyData(infrastructure, CloudFactory.NewDatabaseName()));
        for (var i = 0; i < 3; i++)
        {
            await legacy.AccountAsync("Player" + i, [1, 1], thumbnails: 1, quotaBytes: 100 * MiB, Cancel);
        }

        var steps = new ConcurrentQueue<(string Instance, DateTimeOffset At)>();
        var started = new ConcurrentDictionary<string, DateTimeOffset>();
        CloudFactory Instance(string name) => Track(new CloudFactory(infrastructure, legacy.DatabaseName)
        {
            TestServices = services => services.AddSingleton<IMigrationObserver>(new Recording(name, steps)),
        });
        var first = Instance("a");
        var second = Instance("b");

        await Task.WhenAll(
            Task.Run(() => { _ = first.Services; started["a"] = DateTimeOffset.UtcNow; }, Cancel),
            Task.Run(() => { _ = second.Services; started["b"] = DateTimeOffset.UtcNow; }, Cancel));

        var migrators = steps.Select(step => step.Instance).Distinct().ToList();
        var migrator = Assert.Single(migrators);
        var other = migrator == "a" ? "b" : "a";
        Assert.True(started[other] >= steps.Max(step => step.At));
        Assert.Equal(3, await AuditEntryDocument.In(legacy.Database).CountDocumentsAsync(entry => entry.Kind == AuditKinds.AccountMigrated, cancellationToken: Cancel));
        Assert.Equal(3, await IdentityDocument.In(legacy.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
        Assert.Equal(3, await AccountDocument.In(legacy.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    private static async Task<Snapshot> SnapshotAsync(LegacyData legacy)
    {
        async Task<List<BsonDocument>> All(string collection, string sort = "_id") =>
            await legacy.Database.GetCollection<BsonDocument>(collection).Find(_ => true).Sort(new BsonDocument(sort, 1)).ToListAsync(Cancel);

        var blobs = await legacy.KeysAsync("accounts/", Cancel);
        return new Snapshot(await All("accounts"), await All("identities"), await All("account_audit"), await All("sessions"),
            await All("schematics"), await All("versions"), await All("account_migrations"), [.. blobs.Order(StringComparer.Ordinal)]);
    }

    private static void DisposeCrashed(CloudFactory factory)
    {
        try
        {
            factory.Dispose();
        }
        catch (InvalidOperationException)
        {
            // A host that failed to start can fail again while stopping.
        }
    }

    private T Track<T>(T disposable)
        where T : IDisposable
    {
        disposables.Add(disposable);
        return disposable;
    }

    private sealed record Snapshot(
        List<BsonDocument> Accounts,
        List<BsonDocument> Identities,
        List<BsonDocument> Audit,
        List<BsonDocument> Sessions,
        List<BsonDocument> Schematics,
        List<BsonDocument> Versions,
        List<BsonDocument> Migrations,
        List<string> Blobs);

    private sealed class FailAfter(string uuid, MigrationStep step) : IMigrationObserver
    {
        public Task StepDoneAsync(string legacyUuid, MigrationStep done, CancellationToken cancellationToken) =>
            legacyUuid == uuid && done == step ? throw new InvalidOperationException("Injected fault.") : Task.CompletedTask;
    }

    private sealed class Recording(string instance, ConcurrentQueue<(string Instance, DateTimeOffset At)> steps) : IMigrationObserver
    {
        public async Task StepDoneAsync(string legacyUuid, MigrationStep step, CancellationToken cancellationToken)
        {
            steps.Enqueue((instance, DateTimeOffset.UtcNow));
            await Task.Delay(50, cancellationToken);
        }
    }
}

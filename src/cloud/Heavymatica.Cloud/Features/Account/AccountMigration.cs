using System.Diagnostics;
using System.Security.Cryptography;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Features.Schematics;
using Heavymatica.Cloud.Infrastructure.S3;
using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Account;

/// <summary>Progress of one legacy account, in the order the steps run.</summary>
internal enum MigrationStep
{
    Started,
    AccountCreated,
    FilesCopied,
    OwnersMoved,
    SessionsMoved,
    Finished,
}

/// <summary>One document per legacy UUID, plus the <c>lock</c> document that lets one instance migrate at a time.</summary>
internal sealed class MigrationDocument
{
    public const string Collection = "account_migrations";
    public const string LockId = "lock";

    [BsonId]
    public required string Id { get; init; }

    public string? AccountId { get; init; }

    public MigrationStep Step { get; init; }

    public DateTime StartedAt { get; init; }

    public DateTime? FinishedAt { get; init; }

    public string? Holder { get; init; }

    public DateTime? LeaseUntil { get; init; }

    public static IMongoCollection<MigrationDocument> In(IMongoDatabase database) => database.GetCollection<MigrationDocument>(Collection);
}

/// <summary>An account as it was stored when the Minecraft UUID was the account ID.</summary>
internal sealed class LegacyAccountDocument
{
    [BsonId]
    public required string Id { get; init; }

    public string? Name { get; init; }

    public long UsedBytes { get; init; }

    public long QuotaBytes { get; init; }

    public DateTimeOffset CreatedAt { get; init; }
}

/// <summary>Told after every completed step of an account.</summary>
internal interface IMigrationObserver
{
    Task StepDoneAsync(string legacyUuid, MigrationStep step, CancellationToken cancellationToken);
}

internal sealed class NoMigrationObserver : IMigrationObserver
{
    public Task StepDoneAsync(string legacyUuid, MigrationStep step, CancellationToken cancellationToken) => Task.CompletedTask;
}

/// <summary>
/// Moves every account keyed by a Minecraft UUID to a new account ID with one Minecraft identity, before the service
/// accepts requests. Each step is idempotent and recorded, so an interrupted run completes on the next start.
/// </summary>
internal sealed partial class AccountMigration(
    IMongoDatabase database,
    BlobStore blobs,
    TimeProvider time,
    IMigrationObserver observer,
    ILogger<AccountMigration> logger) : IHostedService
{
    public static readonly TimeSpan LeaseLength = TimeSpan.FromMinutes(2);

    private static readonly BsonRegularExpression LegacyId = new("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private readonly IMongoCollection<MigrationDocument> migrations = MigrationDocument.In(database);
    private readonly IMongoCollection<LegacyAccountDocument> legacyAccounts = database.GetCollection<LegacyAccountDocument>(AccountDocument.Collection);
    private readonly string holder = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(8));

    public TimeSpan LockPollInterval { get; init; } = TimeSpan.FromMilliseconds(250);

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        while (await PendingAsync(cancellationToken) is { Count: > 0 })
        {
            if (!await TryLockAsync(cancellationToken))
            {
                await Task.Delay(LockPollInterval, cancellationToken);
                continue;
            }

            try
            {
                await RunLockedAsync(cancellationToken);
            }
            finally
            {
                await UnlockAsync();
            }
        }
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;

    private async Task RunLockedAsync(CancellationToken cancellationToken)
    {
        using var renewal = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        var renewing = RenewLeaseAsync(renewal.Token);
        try
        {
            var pending = await PendingAsync(cancellationToken);
            var watch = Stopwatch.StartNew();
            foreach (var uuid in pending)
            {
                await MigrateAsync(uuid, cancellationToken);
            }

            if (pending.Count > 0)
            {
                Migrated(logger, pending.Count, watch.Elapsed.TotalSeconds);
            }
        }
        finally
        {
            await renewal.CancelAsync();
            await renewing;
        }
    }

    /// <summary>Legacy UUIDs with work left: legacy account documents, and migrations that never finished.</summary>
    private async Task<List<string>> PendingAsync(CancellationToken cancellationToken)
    {
        var legacy = await legacyAccounts.Find(Builders<LegacyAccountDocument>.Filter.Regex(account => account.Id, LegacyId))
            .Project(account => account.Id).ToListAsync(cancellationToken);
        var unfinished = await migrations.Find(migration => migration.Id != MigrationDocument.LockId && migration.FinishedAt == null)
            .Project(migration => migration.Id).ToListAsync(cancellationToken);
        return [.. legacy.Union(unfinished, StringComparer.Ordinal).Order(StringComparer.Ordinal)];
    }

    private async Task MigrateAsync(string uuid, CancellationToken cancellationToken)
    {
        var migration = await migrations.FindOneAndUpdateAsync(
            Builders<MigrationDocument>.Filter.Eq(candidate => candidate.Id, uuid),
            Builders<MigrationDocument>.Update
                .SetOnInsert(candidate => candidate.AccountId, AccountDocument.NewId())
                .SetOnInsert(candidate => candidate.Step, MigrationStep.Started)
                .SetOnInsert(candidate => candidate.StartedAt, time.GetUtcNow().UtcDateTime),
            new FindOneAndUpdateOptions<MigrationDocument> { IsUpsert = true, ReturnDocument = ReturnDocument.After },
            cancellationToken);
        var accountId = migration.AccountId!;
        var legacy = await legacyAccounts.Find(account => account.Id == uuid).FirstOrDefaultAsync(cancellationToken);

        if (migration.Step < MigrationStep.AccountCreated)
        {
            if (legacy is null)
            {
                throw new InvalidOperationException($"The migration of {uuid} started, but its legacy account document is gone.");
            }

            await CreateAccountAsync(legacy, accountId, cancellationToken);
            await RecordAsync(uuid, MigrationStep.AccountCreated, cancellationToken);
        }

        if (migration.Step < MigrationStep.FilesCopied)
        {
            await CopyFilesAsync(uuid, accountId, cancellationToken);
            await RecordAsync(uuid, MigrationStep.FilesCopied, cancellationToken);
        }

        if (migration.Step < MigrationStep.OwnersMoved)
        {
            await MoveOwnersAsync(uuid, accountId, cancellationToken);
            await RecordAsync(uuid, MigrationStep.OwnersMoved, cancellationToken);
        }

        var identity = await IdentityDocument.In(database)
            .Find(candidate => candidate.Provider == Providers.Minecraft && candidate.Subject == uuid).SingleAsync(cancellationToken);
        if (migration.Step < MigrationStep.SessionsMoved)
        {
            await SessionDocument.In(database).UpdateManyAsync(
                session => session.AccountId == uuid,
                Builders<SessionDocument>.Update
                    .Set(session => session.AccountId, accountId)
                    .Set(session => session.IdentityId, identity.Id)
                    .Set(session => session.Kind, SessionKinds.Game),
                cancellationToken: cancellationToken);
            await RecordAsync(uuid, MigrationStep.SessionsMoved, cancellationToken);
        }

        var audit = AuditEntryDocument.In(database);
        if (!await audit.Find(entry => entry.AccountId == accountId && entry.Kind == AuditKinds.AccountMigrated).AnyAsync(cancellationToken))
        {
            await audit.InsertOneAsync(new AuditEntryDocument
            {
                Id = ObjectId.GenerateNewId(),
                AccountId = accountId,
                At = time.GetUtcNow().UtcDateTime,
                Kind = AuditKinds.AccountMigrated,
                Actor = new AuditActor(Providers.Minecraft, identity.Id),
            }, cancellationToken: cancellationToken);
        }

        await legacyAccounts.DeleteOneAsync(account => account.Id == uuid, cancellationToken);
        await migrations.UpdateOneAsync(candidate => candidate.Id == uuid,
            Builders<MigrationDocument>.Update.Set(candidate => candidate.Step, MigrationStep.Finished)
                .Set(candidate => candidate.FinishedAt, time.GetUtcNow().UtcDateTime),
            cancellationToken: cancellationToken);
        await observer.StepDoneAsync(uuid, MigrationStep.Finished, cancellationToken);
    }

    private async Task CreateAccountAsync(LegacyAccountDocument legacy, string accountId, CancellationToken cancellationToken)
    {
        await AccountDocument.In(database).UpdateOneAsync(
            account => account.Id == accountId,
            Builders<AccountDocument>.Update
                .SetOnInsert(account => account.UsedBytes, legacy.UsedBytes)
                .SetOnInsert(account => account.QuotaBytes, legacy.QuotaBytes)
                .SetOnInsert(account => account.CreatedAt, legacy.CreatedAt),
            new UpdateOptions { IsUpsert = true },
            cancellationToken);

        var now = time.GetUtcNow().UtcDateTime;
        await IdentityDocument.In(database).UpdateOneAsync(
            identity => identity.Provider == Providers.Minecraft && identity.Subject == legacy.Id,
            Builders<IdentityDocument>.Update
                .SetOnInsert(identity => identity.Id, AccountDocument.NewId())
                .SetOnInsert(identity => identity.AccountId, accountId)
                .SetOnInsert(identity => identity.Name, legacy.Name)
                .SetOnInsert(identity => identity.LinkedAt, legacy.CreatedAt.UtcDateTime)
                .SetOnInsert(identity => identity.LastSignInAt, now),
            new UpdateOptions { IsUpsert = true },
            cancellationToken);
    }

    private async Task CopyFilesAsync(string uuid, string accountId, CancellationToken cancellationToken)
    {
        var source = $"accounts/{uuid}/";
        var target = $"accounts/{accountId}/";
        await foreach (var blob in blobs.ListAsync(source, cancellationToken))
        {
            var targetKey = target + blob.Key[source.Length..];
            if (!await blobs.ExistsAsync(targetKey, cancellationToken))
            {
                await blobs.CopyAsync(blob.Key, targetKey, cancellationToken);
            }
        }
    }

    private async Task MoveOwnersAsync(string uuid, string accountId, CancellationToken cancellationToken)
    {
        await SchematicDocument.In(database).UpdateManyAsync(
            schematic => schematic.Owner == uuid,
            Builders<SchematicDocument>.Update.Set(schematic => schematic.Owner, accountId).Set(schematic => schematic.Latest.Owner, accountId),
            cancellationToken: cancellationToken);
        await VersionDocument.In(database).UpdateManyAsync(
            version => version.Owner == uuid,
            Builders<VersionDocument>.Update.Set(version => version.Owner, accountId),
            cancellationToken: cancellationToken);
    }

    private async Task RecordAsync(string uuid, MigrationStep step, CancellationToken cancellationToken)
    {
        await migrations.UpdateOneAsync(candidate => candidate.Id == uuid,
            Builders<MigrationDocument>.Update.Set(candidate => candidate.Step, step), cancellationToken: cancellationToken);
        await observer.StepDoneAsync(uuid, step, cancellationToken);
    }

    private async Task<bool> TryLockAsync(CancellationToken cancellationToken)
    {
        var now = time.GetUtcNow().UtcDateTime;
        var free = Builders<MigrationDocument>.Filter.Eq(candidate => candidate.Id, MigrationDocument.LockId)
            & (Builders<MigrationDocument>.Filter.Eq(candidate => candidate.Holder, null)
                | Builders<MigrationDocument>.Filter.Eq(candidate => candidate.Holder, holder)
                | Builders<MigrationDocument>.Filter.Lt(candidate => candidate.LeaseUntil, now));
        try
        {
            await migrations.UpdateOneAsync(free,
                Builders<MigrationDocument>.Update.Set(candidate => candidate.Holder, holder).Set(candidate => candidate.LeaseUntil, now + LeaseLength)
                    .SetOnInsert(candidate => candidate.StartedAt, now),
                new UpdateOptions { IsUpsert = true },
                cancellationToken);
            return true;
        }
        catch (MongoWriteException e) when (e.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            return false;
        }
    }

    private async Task RenewLeaseAsync(CancellationToken cancellationToken)
    {
        try
        {
            while (true)
            {
                await Task.Delay(LeaseLength / 4, cancellationToken);
                await migrations.UpdateOneAsync(
                    candidate => candidate.Id == MigrationDocument.LockId && candidate.Holder == holder,
                    Builders<MigrationDocument>.Update.Set(candidate => candidate.LeaseUntil, time.GetUtcNow().UtcDateTime + LeaseLength),
                    cancellationToken: cancellationToken);
            }
        }
        catch (OperationCanceledException)
        {
        }
    }

    private async Task UnlockAsync() =>
        await migrations.UpdateOneAsync(
            candidate => candidate.Id == MigrationDocument.LockId && candidate.Holder == holder,
            Builders<MigrationDocument>.Update.Set(candidate => candidate.Holder, null).Set(candidate => candidate.LeaseUntil, null),
            cancellationToken: CancellationToken.None);

    [LoggerMessage(EventId = 1101, Level = LogLevel.Information, Message = "Migrated {Count} legacy accounts in {Seconds:F1} s.")]
    private static partial void Migrated(ILogger logger, int count, double seconds);
}

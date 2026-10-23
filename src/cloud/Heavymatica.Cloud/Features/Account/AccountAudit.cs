using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Account;

internal static class AuditKinds
{
    public const string AccountCreated = "account_created";
    public const string AccountMigrated = "account_migrated";
    public const string IdentityAdded = "identity_added";
    public const string IdentityRemoved = "identity_removed";
}

internal sealed record AuditActor(string Provider, string IdentityId);

internal sealed class AuditEntryDocument
{
    public const string Collection = "account_audit";

    [BsonId]
    public ObjectId Id { get; init; }

    public required string AccountId { get; init; }

    public DateTime At { get; init; }

    public required string Kind { get; init; }

    public required AuditActor Actor { get; init; }

    /// <summary>The identity added or removed, when it is not the actor.</summary>
    public string? IdentityId { get; init; }

    public static IMongoCollection<AuditEntryDocument> In(IMongoDatabase database) => database.GetCollection<AuditEntryDocument>(Collection);
}

/// <summary>Kept for as long as the account exists. There is no API to read it yet.</summary>
internal sealed class AccountAudit(IMongoDatabase database, TimeProvider time)
{
    private readonly IMongoCollection<AuditEntryDocument> entries = AuditEntryDocument.In(database);

    public Task RecordAsync(string accountId, string kind, AuditActor actor, CancellationToken cancellationToken, string? identityId = null) =>
        entries.InsertOneAsync(new AuditEntryDocument
        {
            Id = ObjectId.GenerateNewId(),
            AccountId = accountId,
            At = time.GetUtcNow().UtcDateTime,
            Kind = kind,
            Actor = actor,
            IdentityId = identityId,
        }, cancellationToken: cancellationToken);
}

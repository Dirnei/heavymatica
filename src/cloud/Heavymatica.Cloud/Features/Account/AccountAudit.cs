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
    public const string LinkCodeIssued = "link_code_issued";
    public const string LinkCodeRedeemed = "link_code_redeemed";
    public const string LinkCodeRedeemFailed = "link_code_redeem_failed";
    public const string LinkCodeConfirmed = "link_code_confirmed";
    public const string LinkCodeDeclined = "link_code_declined";
}

/// <summary>
/// The identity that acted. A pending sign-in has no identity document yet and is named by its provider subject.
/// </summary>
internal sealed record AuditActor(string Provider, string? IdentityId, string? Subject = null);

internal sealed class AuditEntryDocument
{
    public const string Collection = "account_audit";

    [BsonId]
    public ObjectId Id { get; init; }

    /// <summary>Absent on code entries of a pending actor that no account is involved in yet.</summary>
    public string? AccountId { get; init; }

    public DateTime At { get; init; }

    public required string Kind { get; init; }

    public required AuditActor Actor { get; init; }

    /// <summary>The identity added or removed, when it is not the actor.</summary>
    public string? IdentityId { get; init; }

    public string? CodeId { get; init; }

    /// <summary>The client address, on code entries.</summary>
    public string? Ip { get; init; }

    /// <summary>When a TTL index removes the entry; absent on entries kept for as long as the account exists.</summary>
    public DateTime? ExpireAt { get; init; }

    public static IMongoCollection<AuditEntryDocument> In(IMongoDatabase database) => database.GetCollection<AuditEntryDocument>(Collection);
}

/// <summary>
/// Account entries are kept for as long as the account exists, code entries for a limited time. There is no API to
/// read it yet.
/// </summary>
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

    public Task RecordCodeAsync(string? accountId, string kind, AuditActor actor, string? codeId, string? ip, TimeSpan retention,
        CancellationToken cancellationToken)
    {
        var now = time.GetUtcNow().UtcDateTime;
        return entries.InsertOneAsync(new AuditEntryDocument
        {
            Id = ObjectId.GenerateNewId(),
            AccountId = accountId,
            At = now,
            Kind = kind,
            Actor = actor,
            CodeId = codeId,
            Ip = ip,
            ExpireAt = now + retention,
        }, cancellationToken: cancellationToken);
    }

    /// <summary>Entries of one kind by one actor since the given time.</summary>
    public Task<long> CountSinceAsync(string kind, AuditActor actor, DateTime since, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(actor);
        var filter = Builders<AuditEntryDocument>.Filter;
        return entries.CountDocumentsAsync(
            filter.Eq(entry => entry.Kind, kind)
                & filter.Eq(entry => entry.Actor.Provider, actor.Provider)
                & filter.Eq(entry => entry.Actor.IdentityId, actor.IdentityId)
                & filter.Eq(entry => entry.Actor.Subject, actor.Subject)
                & filter.Gte(entry => entry.At, since),
            cancellationToken: cancellationToken);
    }
}

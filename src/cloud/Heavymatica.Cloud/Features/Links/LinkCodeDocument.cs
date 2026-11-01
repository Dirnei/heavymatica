using Heavymatica.Cloud.Infrastructure.Mongo;
using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Links;

/// <summary>Stored states. <c>expired</c> is never stored; it is read from <c>expiresAt</c> and <c>confirmBy</c>.</summary>
internal static class LinkCodeStates
{
    public const string Open = "open";
    public const string Redeemed = "redeemed";
    public const string Linked = "linked";
    public const string Declined = "declined";
    public const string Cancelled = "cancelled";
}

internal static class LinkPartyKinds
{
    /// <summary>A signed-in account, through one of its identities.</summary>
    public const string Account = "account";

    /// <summary>A verified identity without an account.</summary>
    public const string Pending = "pending";
}

/// <summary>
/// The issuer or redeemer of a code: an account and the identity it signed in with, or a pending identity named by
/// its provider and subject.
/// </summary>
internal sealed record LinkPartyDocument
{
    public required string Kind { get; init; }

    public string? AccountId { get; init; }

    public string? IdentityId { get; init; }

    public required string Provider { get; init; }

    /// <summary>The provider subject of a pending party.</summary>
    public string? Subject { get; init; }

    public string? Name { get; init; }
}

/// <summary>A link code. Only the hash of the code is kept. Documents are removed a day after the code expires.</summary>
internal sealed class LinkCodeDocument
{
    public const string Collection = "link_codes";

    /// <summary>32 lower-case hex characters, unrelated to the code.</summary>
    [BsonId]
    public required string Id { get; init; }

    public required string CodeHash { get; init; }

    public required LinkPartyDocument Issuer { get; init; }

    public required string Status { get; init; }

    public DateTime CreatedAt { get; init; }

    public DateTime ExpiresAt { get; init; }

    public LinkPartyDocument? Redeemer { get; init; }

    public DateTime? RedeemedAt { get; init; }

    public DateTime? ConfirmBy { get; init; }

    public DateTime? ResolvedAt { get; init; }

    public static IMongoCollection<LinkCodeDocument> In(IMongoDatabase database) => database.GetCollection<LinkCodeDocument>(Collection);
}

internal sealed class LinkIndexes : IMongoIndexes
{
    public async Task CreateAsync(IMongoDatabase database, CancellationToken cancellationToken)
    {
        var keys = Builders<LinkCodeDocument>.IndexKeys;
        await LinkCodeDocument.In(database).Indexes.CreateManyAsync(
        [
            new CreateIndexModel<LinkCodeDocument>(
                keys.Ascending(code => code.CodeHash),
                new CreateIndexOptions<LinkCodeDocument>
                {
                    Name = "codeHash_open",
                    Unique = true,
                    PartialFilterExpression = new BsonDocument("status", LinkCodeStates.Open),
                }),
            new CreateIndexModel<LinkCodeDocument>(
                keys.Ascending(code => code.Issuer.Provider).Ascending(code => code.Issuer.IdentityId).Ascending(code => code.Issuer.Subject)
                    .Ascending(code => code.Status),
                new CreateIndexOptions { Name = "issuer_status" }),
            new CreateIndexModel<LinkCodeDocument>(
                keys.Ascending(code => code.ExpiresAt),
                new CreateIndexOptions { Name = "expiresAt_ttl", ExpireAfter = TimeSpan.FromDays(1) }),
        ], cancellationToken);
    }
}

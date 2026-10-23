using System.Security.Cryptography;
using Heavymatica.Cloud.Infrastructure.Mongo;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Account;

/// <summary>Owns schematics, storage usage and quota. It has no name; the identity in use names it.</summary>
internal sealed class AccountDocument
{
    public const string Collection = "accounts";

    /// <summary>32 lower-case hex characters, never derived from an identity.</summary>
    [BsonId]
    public required string Id { get; init; }

    public long UsedBytes { get; set; }

    public long QuotaBytes { get; set; }

    public DateTimeOffset CreatedAt { get; init; }

    public static string NewId() => Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(16));

    public static IMongoCollection<AccountDocument> In(IMongoDatabase database) => database.GetCollection<AccountDocument>(Collection);
}

/// <summary>A way to sign in to an account: a Minecraft UUID or a web provider's user ID. Never an email address.</summary>
internal sealed class IdentityDocument
{
    public const string Collection = "identities";

    [BsonId]
    public required string Id { get; init; }

    public required string AccountId { get; init; }

    public required string Provider { get; init; }

    /// <summary>The dashed Minecraft UUID, or the provider's stable user ID.</summary>
    public required string Subject { get; init; }

    /// <summary>Last known Minecraft name or provider login, for display only.</summary>
    public string? Name { get; set; }

    public DateTime LinkedAt { get; init; }

    public DateTime LastSignInAt { get; set; }

    public static IMongoCollection<IdentityDocument> In(IMongoDatabase database) => database.GetCollection<IdentityDocument>(Collection);
}

internal static class Providers
{
    public const string Minecraft = "minecraft";
    public const string GitHub = "github";
}

internal sealed class AccountIndexes : IMongoIndexes
{
    public async Task CreateAsync(IMongoDatabase database, CancellationToken cancellationToken)
    {
        var keys = Builders<IdentityDocument>.IndexKeys;
        await IdentityDocument.In(database).Indexes.CreateManyAsync(
        [
            new CreateIndexModel<IdentityDocument>(
                keys.Ascending(identity => identity.Provider).Ascending(identity => identity.Subject),
                new CreateIndexOptions { Name = "provider_subject", Unique = true }),
            new CreateIndexModel<IdentityDocument>(
                keys.Ascending(identity => identity.AccountId),
                new CreateIndexOptions { Name = "accountId" }),
        ], cancellationToken);

        await AuditEntryDocument.In(database).Indexes.CreateOneAsync(new CreateIndexModel<AuditEntryDocument>(
            Builders<AuditEntryDocument>.IndexKeys.Ascending(entry => entry.AccountId).Ascending(entry => entry.At),
            new CreateIndexOptions { Name = "accountId_at" }), cancellationToken: cancellationToken);
    }
}

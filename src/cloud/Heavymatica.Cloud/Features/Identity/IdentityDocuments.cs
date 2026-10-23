using Heavymatica.Cloud.Infrastructure.Mongo;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Identity;

/// <summary>Expired documents are removed by a TTL index.</summary>
internal sealed class ChallengeDocument
{
    public const string Collection = "challenges";

    [BsonId]
    public required string Id { get; init; }

    public required string ServerId { get; init; }

    public DateTime ExpiresAt { get; init; }

    public bool Used { get; init; }

    public static IMongoCollection<ChallengeDocument> In(IMongoDatabase database) => database.GetCollection<ChallengeDocument>(Collection);
}

internal static class SessionKinds
{
    /// <summary>The mod: access tokens and a rotating refresh token.</summary>
    public const string Game = "game";

    /// <summary>The website: a session cookie, no refresh token.</summary>
    public const string Web = "web";
}

/// <summary>
/// A sign-in session. For the game it is the family every rotated refresh token of one sign-in belongs to: only the
/// hash of the current refresh token is stored, and presenting an older one revokes the family. Idle sessions expire
/// after 30 days.
/// </summary>
internal sealed class SessionDocument
{
    public const string Collection = "sessions";

    [BsonId]
    public required string Id { get; init; }

    public required string AccountId { get; init; }

    /// <summary>The identity that signed in; it must still belong to the account for the session to stay valid.</summary>
    public string? IdentityId { get; init; }

    public string Kind { get; init; } = SessionKinds.Game;

    public string? CurrentRefreshHash { get; init; }

    public bool Revoked { get; init; }

    public DateTime CreatedAt { get; init; }

    public DateTime LastUsedAt { get; init; }

    public static IMongoCollection<SessionDocument> In(IMongoDatabase database) => database.GetCollection<SessionDocument>(Collection);
}

internal sealed class IdentityIndexes : IMongoIndexes
{
    public static readonly TimeSpan SessionIdleLifetime = TimeSpan.FromDays(30);

    public async Task CreateAsync(IMongoDatabase database, CancellationToken cancellationToken)
    {
        await ChallengeDocument.In(database).Indexes.CreateOneAsync(new CreateIndexModel<ChallengeDocument>(
            Builders<ChallengeDocument>.IndexKeys.Ascending(challenge => challenge.ExpiresAt),
            new CreateIndexOptions { Name = "expiresAt_ttl", ExpireAfter = TimeSpan.Zero }), cancellationToken: cancellationToken);

        var sessions = SessionDocument.In(database);
        await sessions.Indexes.CreateManyAsync(
        [
            new CreateIndexModel<SessionDocument>(
                Builders<SessionDocument>.IndexKeys.Ascending(session => session.LastUsedAt),
                new CreateIndexOptions { Name = "lastUsedAt_ttl", ExpireAfter = SessionIdleLifetime }),
            new CreateIndexModel<SessionDocument>(
                Builders<SessionDocument>.IndexKeys.Ascending(session => session.AccountId),
                new CreateIndexOptions { Name = "accountId" }),
        ], cancellationToken);
    }
}

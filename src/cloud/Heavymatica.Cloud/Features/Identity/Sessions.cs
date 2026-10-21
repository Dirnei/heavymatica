using System.Security.Cryptography;
using System.Text;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Identity;

/// <summary>
/// Refresh tokens. A token is <c>{sessionId}.{secret}</c>; only the SHA-256 of the current token is stored.
/// Exchanging a token rotates it; presenting a token that was already exchanged revokes the whole session.
/// </summary>
internal sealed class Sessions(IMongoDatabase database, TimeProvider time)
{
    private readonly IMongoCollection<SessionDocument> sessions = SessionDocument.In(database);

    public async Task<(string SessionId, string RefreshToken)> StartAsync(string accountId, CancellationToken cancellationToken)
    {
        var sessionId = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(16));
        var token = NewToken(sessionId);
        var now = time.GetUtcNow().UtcDateTime;
        await sessions.InsertOneAsync(new SessionDocument
        {
            Id = sessionId,
            AccountId = accountId,
            CurrentRefreshHash = Hash(token),
            CreatedAt = now,
            LastUsedAt = now,
        }, cancellationToken: cancellationToken);
        return (sessionId, token);
    }

    /// <summary>The account, session and new refresh token, or null when the token is not (or no longer) valid.</summary>
    public async Task<(string AccountId, string SessionId, string RefreshToken)?> RotateAsync(string refreshToken, CancellationToken cancellationToken)
    {
        if (SessionIdOf(refreshToken) is not { } sessionId)
        {
            return null;
        }

        var session = await sessions.Find(candidate => candidate.Id == sessionId).FirstOrDefaultAsync(cancellationToken);
        var now = time.GetUtcNow().UtcDateTime;
        if (session is null || session.Revoked || session.LastUsedAt + IdentityIndexes.SessionIdleLifetime <= now)
        {
            return null;
        }

        var presented = Hash(refreshToken);
        if (!CryptographicOperations.FixedTimeEquals(Encoding.ASCII.GetBytes(presented), Encoding.ASCII.GetBytes(session.CurrentRefreshHash)))
        {
            await RevokeSessionAsync(sessionId, cancellationToken);
            return null;
        }

        // Conditional on the presented hash, so two concurrent refreshes with the same token cannot both succeed.
        var next = NewToken(sessionId);
        var rotated = await sessions.UpdateOneAsync(
            Builders<SessionDocument>.Filter.Eq(candidate => candidate.Id, sessionId)
                & Builders<SessionDocument>.Filter.Eq(candidate => candidate.CurrentRefreshHash, presented)
                & Builders<SessionDocument>.Filter.Eq(candidate => candidate.Revoked, false),
            Builders<SessionDocument>.Update.Set(candidate => candidate.CurrentRefreshHash, Hash(next)).Set(candidate => candidate.LastUsedAt, now),
            cancellationToken: cancellationToken);
        if (rotated.ModifiedCount == 0)
        {
            await RevokeSessionAsync(sessionId, cancellationToken);
            return null;
        }

        return (session.AccountId, sessionId, next);
    }

    /// <summary>Revokes the session the token belongs to. Unknown or invalid tokens are ignored.</summary>
    public async Task RevokeAsync(string refreshToken, CancellationToken cancellationToken)
    {
        if (SessionIdOf(refreshToken) is not { } sessionId)
        {
            return;
        }

        await sessions.UpdateOneAsync(
            Builders<SessionDocument>.Filter.Eq(candidate => candidate.Id, sessionId)
                & Builders<SessionDocument>.Filter.Eq(candidate => candidate.CurrentRefreshHash, Hash(refreshToken)),
            Builders<SessionDocument>.Update.Set(candidate => candidate.Revoked, true),
            cancellationToken: cancellationToken);
    }

    private Task<UpdateResult> RevokeSessionAsync(string sessionId, CancellationToken cancellationToken) =>
        sessions.UpdateOneAsync(candidate => candidate.Id == sessionId, Builders<SessionDocument>.Update.Set(candidate => candidate.Revoked, true),
            cancellationToken: cancellationToken);

    private static string NewToken(string sessionId) =>
        sessionId + "." + Base64Url(RandomNumberGenerator.GetBytes(32));

    private static string? SessionIdOf(string token)
    {
        var dot = token.IndexOf('.', StringComparison.Ordinal);
        return dot is 32 && token.Length > 33 ? token[..dot] : null;
    }

    private static string Hash(string token) => Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

    private static string Base64Url(byte[] bytes) => Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}

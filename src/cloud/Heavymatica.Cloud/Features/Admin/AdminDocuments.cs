using System.Security.Cryptography;
using Heavymatica.Cloud.Infrastructure.Mongo;
using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>An admin: a principal of its own, with passkeys and nothing else to sign in with.</summary>
internal sealed class AdminUser
{
    public const string Collection = "admins";

    /// <summary>32 lower-case hex characters. Also the passkeys' user handle and the Identity user name.</summary>
    [BsonId]
    public required string Id { get; init; }

    public required string DisplayName { get; set; }

    public DateTime CreatedAt { get; init; }

    public static string NewId() => Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(16));

    public static IMongoCollection<AdminUser> In(IMongoDatabase database) => database.GetCollection<AdminUser>(Collection);
}

/// <summary>A registered passkey. The ID is the credential ID, base64url.</summary>
internal sealed class AdminPasskeyDocument
{
    public const string Collection = "admin_passkeys";

    [BsonId]
    public required string Id { get; init; }

    public required string AdminId { get; init; }

    public required byte[] PublicKey { get; init; }

    public long SignCount { get; set; }

    public string[]? Transports { get; init; }

    public bool IsUserVerified { get; set; }

    public bool IsBackupEligible { get; init; }

    public bool IsBackedUp { get; set; }

    public byte[]? AttestationObject { get; init; }

    public byte[]? ClientDataJson { get; init; }

    public string? Name { get; set; }

    public DateTime CreatedAt { get; init; }

    public DateTime? LastUsedAt { get; set; }

    public static IMongoCollection<AdminPasskeyDocument> In(IMongoDatabase database) => database.GetCollection<AdminPasskeyDocument>(Collection);
}

/// <summary>An admin session, checked on every request. Removed a day after it started.</summary>
internal sealed class AdminSessionDocument
{
    public const string Collection = "admin_sessions";

    [BsonId]
    public required string Id { get; init; }

    public required string AdminId { get; init; }

    public DateTime CreatedAt { get; init; }

    public DateTime LastUsedAt { get; init; }

    public DateTime? EndedAt { get; init; }

    public static IMongoCollection<AdminSessionDocument> In(IMongoDatabase database) => database.GetCollection<AdminSessionDocument>(Collection);
}

/// <summary>The one setup code from the server command. Only its hash is kept; a new code replaces it.</summary>
internal sealed class SetupCodeDocument
{
    public const string Collection = "admin_setup_codes";

    public const string CurrentId = "current";

    [BsonId]
    public required string Id { get; init; }

    public required string CodeHash { get; init; }

    /// <summary>The admin a recovery code adds a passkey to; absent on a code that creates a new admin.</summary>
    public string? AdminId { get; init; }

    public DateTime CreatedAt { get; init; }

    public DateTime ExpiresAt { get; init; }

    public DateTime? UsedAt { get; init; }

    public static IMongoCollection<SetupCodeDocument> In(IMongoDatabase database) => database.GetCollection<SetupCodeDocument>(Collection);
}

internal static class InvitationKinds
{
    public const string Admin = "admin";
    public const string Recovery = "recovery";
}

/// <summary>An invitation link. Only the hash of its token is kept. Removed 30 days after it expired.</summary>
internal sealed class InvitationDocument
{
    public const string Collection = "admin_invitations";

    /// <summary>32 lower-case hex characters, unrelated to the token.</summary>
    [BsonId]
    public required string Id { get; init; }

    public required string TokenHash { get; init; }

    public required string Kind { get; init; }

    /// <summary>The admin a recovery invitation adds a passkey to.</summary>
    public string? AdminId { get; init; }

    public required string CreatedBy { get; init; }

    public DateTime CreatedAt { get; init; }

    public DateTime ExpiresAt { get; init; }

    public DateTime? UsedAt { get; init; }

    public DateTime? RevokedAt { get; init; }

    public static IMongoCollection<InvitationDocument> In(IMongoDatabase database) => database.GetCollection<InvitationDocument>(Collection);
}

internal static class AdminAuditKinds
{
    public const string SignedIn = "admin_signed_in";
    public const string SignInFailed = "admin_sign_in_failed";
    public const string SetupCodeFailed = "setup_code_failed";
    public const string InvitationFailed = "invitation_failed";
    public const string AdminCreated = "admin_created";
    public const string AdminRemoved = "admin_removed";
    public const string PasskeyAdded = "passkey_added";
    public const string PasskeyRemoved = "passkey_removed";
    public const string InvitationCreated = "invitation_created";
    public const string InvitationRevoked = "invitation_revoked";
    public const string QuotaChanged = "quota_changed";
}

internal sealed class AdminAuditDocument
{
    public const string Collection = "admin_audit";

    [BsonId]
    public ObjectId Id { get; init; }

    public DateTime At { get; init; }

    public string? AdminId { get; init; }

    /// <summary>The acting admin's display name at the time, so entries stay readable after the admin is removed.</summary>
    public string? AdminName { get; init; }

    public required string Ip { get; init; }

    public required string Kind { get; init; }

    public string? Target { get; init; }

    public Dictionary<string, string> Details { get; init; } = [];

    public DateTime ExpireAt { get; init; }

    public static IMongoCollection<AdminAuditDocument> In(IMongoDatabase database) => database.GetCollection<AdminAuditDocument>(Collection);
}

internal sealed class AdminIndexes : IMongoIndexes
{
    public async Task CreateAsync(IMongoDatabase database, CancellationToken cancellationToken)
    {
        await AdminPasskeyDocument.In(database).Indexes.CreateOneAsync(new CreateIndexModel<AdminPasskeyDocument>(
            Builders<AdminPasskeyDocument>.IndexKeys.Ascending(passkey => passkey.AdminId),
            new CreateIndexOptions { Name = "adminId" }), cancellationToken: cancellationToken);

        var sessions = Builders<AdminSessionDocument>.IndexKeys;
        await AdminSessionDocument.In(database).Indexes.CreateManyAsync(
        [
            new CreateIndexModel<AdminSessionDocument>(sessions.Ascending(session => session.AdminId), new CreateIndexOptions { Name = "adminId" }),
            new CreateIndexModel<AdminSessionDocument>(sessions.Ascending(session => session.CreatedAt),
                new CreateIndexOptions { Name = "createdAt_ttl", ExpireAfter = TimeSpan.FromDays(1) }),
        ], cancellationToken);

        await SetupCodeDocument.In(database).Indexes.CreateOneAsync(new CreateIndexModel<SetupCodeDocument>(
            Builders<SetupCodeDocument>.IndexKeys.Ascending(code => code.ExpiresAt),
            new CreateIndexOptions { Name = "expiresAt_ttl", ExpireAfter = TimeSpan.FromDays(1) }), cancellationToken: cancellationToken);

        var invitations = Builders<InvitationDocument>.IndexKeys;
        await InvitationDocument.In(database).Indexes.CreateManyAsync(
        [
            new CreateIndexModel<InvitationDocument>(invitations.Ascending(invitation => invitation.TokenHash),
                new CreateIndexOptions { Name = "tokenHash", Unique = true }),
            new CreateIndexModel<InvitationDocument>(invitations.Ascending(invitation => invitation.ExpiresAt),
                new CreateIndexOptions { Name = "expiresAt_ttl", ExpireAfter = TimeSpan.FromDays(30) }),
        ], cancellationToken);

        var audit = Builders<AdminAuditDocument>.IndexKeys;
        await AdminAuditDocument.In(database).Indexes.CreateManyAsync(
        [
            new CreateIndexModel<AdminAuditDocument>(audit.Descending(entry => entry.At).Descending(entry => entry.Id), new CreateIndexOptions { Name = "at_id" }),
            new CreateIndexModel<AdminAuditDocument>(audit.Ascending(entry => entry.ExpireAt),
                new CreateIndexOptions { Name = "expireAt_ttl", ExpireAfter = TimeSpan.Zero }),
        ], cancellationToken);
    }
}

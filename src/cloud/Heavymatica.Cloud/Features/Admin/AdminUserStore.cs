using System.Buffers.Text;
using Microsoft.AspNetCore.Identity;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>
/// Identity's view of the admins: users and their passkeys, nothing else. Without password, email, phone or lockout
/// stores Identity has no way to sign an admin in except with a passkey. The user name is the admin ID.
/// </summary>
internal sealed class AdminUserStore(IMongoDatabase database, TimeProvider time) : IUserStore<AdminUser>, IUserPasskeyStore<AdminUser>
{
    private readonly IMongoCollection<AdminUser> admins = AdminUser.In(database);
    private readonly IMongoCollection<AdminPasskeyDocument> passkeys = AdminPasskeyDocument.In(database);

    public static string CredentialKey(byte[] credentialId) => Base64Url.EncodeToString(credentialId);

    public static UserPasskeyInfo ToInfo(AdminPasskeyDocument passkey)
    {
        ArgumentNullException.ThrowIfNull(passkey);
        return new UserPasskeyInfo(Base64Url.DecodeFromChars(passkey.Id), passkey.PublicKey, new DateTimeOffset(passkey.CreatedAt, TimeSpan.Zero),
            checked((uint)passkey.SignCount), passkey.Transports, passkey.IsUserVerified, passkey.IsBackupEligible, passkey.IsBackedUp,
            passkey.AttestationObject ?? [], passkey.ClientDataJson ?? [])
        {
            Name = passkey.Name,
        };
    }

    public static AdminPasskeyDocument ToDocument(string adminId, UserPasskeyInfo passkey, DateTime createdAt)
    {
        ArgumentNullException.ThrowIfNull(passkey);
        return new AdminPasskeyDocument
        {
            Id = CredentialKey(passkey.CredentialId),
            AdminId = adminId,
            PublicKey = passkey.PublicKey,
            SignCount = passkey.SignCount,
            Transports = passkey.Transports,
            IsUserVerified = passkey.IsUserVerified,
            IsBackupEligible = passkey.IsBackupEligible,
            IsBackedUp = passkey.IsBackedUp,
            AttestationObject = passkey.AttestationObject,
            ClientDataJson = passkey.ClientDataJson,
            Name = passkey.Name,
            CreatedAt = createdAt,
        };
    }

    public Task<string> GetUserIdAsync(AdminUser user, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        return Task.FromResult(user.Id);
    }

    public Task<string?> GetUserNameAsync(AdminUser user, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        return Task.FromResult<string?>(user.Id);
    }

    public Task SetUserNameAsync(AdminUser user, string? userName, CancellationToken cancellationToken) => Task.CompletedTask;

    public Task<string?> GetNormalizedUserNameAsync(AdminUser user, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        return Task.FromResult<string?>(user.Id.ToUpperInvariant());
    }

    public Task SetNormalizedUserNameAsync(AdminUser user, string? normalizedName, CancellationToken cancellationToken) => Task.CompletedTask;

    public async Task<IdentityResult> CreateAsync(AdminUser user, CancellationToken cancellationToken)
    {
        await admins.InsertOneAsync(user, cancellationToken: cancellationToken);
        return IdentityResult.Success;
    }

    public async Task<IdentityResult> UpdateAsync(AdminUser user, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        await admins.UpdateOneAsync(admin => admin.Id == user.Id, Builders<AdminUser>.Update.Set(admin => admin.DisplayName, user.DisplayName),
            cancellationToken: cancellationToken);
        return IdentityResult.Success;
    }

    public async Task<IdentityResult> DeleteAsync(AdminUser user, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        await admins.DeleteOneAsync(admin => admin.Id == user.Id, cancellationToken);
        await passkeys.DeleteManyAsync(passkey => passkey.AdminId == user.Id, cancellationToken);
        return IdentityResult.Success;
    }

    public async Task<AdminUser?> FindByIdAsync(string userId, CancellationToken cancellationToken) =>
        await admins.Find(admin => admin.Id == userId).FirstOrDefaultAsync(cancellationToken);

    public Task<AdminUser?> FindByNameAsync(string normalizedUserName, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(normalizedUserName);
        return FindByIdAsync(normalizedUserName.ToLowerInvariant(), cancellationToken);
    }

    public async Task AddOrUpdatePasskeyAsync(AdminUser user, UserPasskeyInfo passkey, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        ArgumentNullException.ThrowIfNull(passkey);
        var key = CredentialKey(passkey.CredentialId);
        var updated = await passkeys.UpdateOneAsync(
            candidate => candidate.Id == key && candidate.AdminId == user.Id,
            Builders<AdminPasskeyDocument>.Update
                .Set(candidate => candidate.SignCount, passkey.SignCount)
                .Set(candidate => candidate.IsBackedUp, passkey.IsBackedUp)
                .Set(candidate => candidate.IsUserVerified, passkey.IsUserVerified)
                .Set(candidate => candidate.Name, passkey.Name),
            cancellationToken: cancellationToken);
        if (updated.MatchedCount == 0)
        {
            await passkeys.InsertOneAsync(ToDocument(user.Id, passkey, time.GetUtcNow().UtcDateTime), cancellationToken: cancellationToken);
        }
    }

    public async Task<IList<UserPasskeyInfo>> GetPasskeysAsync(AdminUser user, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        var stored = await passkeys.Find(passkey => passkey.AdminId == user.Id).SortBy(passkey => passkey.CreatedAt).ToListAsync(cancellationToken);
        return stored.Select(ToInfo).ToList();
    }

    public async Task<AdminUser?> FindByPasskeyIdAsync(byte[] credentialId, CancellationToken cancellationToken)
    {
        var key = CredentialKey(credentialId);
        var passkey = await passkeys.Find(candidate => candidate.Id == key).FirstOrDefaultAsync(cancellationToken);
        return passkey is null ? null : await FindByIdAsync(passkey.AdminId, cancellationToken);
    }

    public async Task<UserPasskeyInfo?> FindPasskeyAsync(AdminUser user, byte[] credentialId, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        var key = CredentialKey(credentialId);
        var passkey = await passkeys.Find(candidate => candidate.Id == key && candidate.AdminId == user.Id).FirstOrDefaultAsync(cancellationToken);
        return passkey is null ? null : ToInfo(passkey);
    }

    public async Task RemovePasskeyAsync(AdminUser user, byte[] credentialId, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(user);
        var key = CredentialKey(credentialId);
        await passkeys.DeleteOneAsync(candidate => candidate.Id == key && candidate.AdminId == user.Id, cancellationToken);
    }

    public void Dispose()
    {
    }
}

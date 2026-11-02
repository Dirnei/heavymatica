using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

internal enum RemoveAdminOutcome
{
    Removed,
    NotFound,
    LastAdmin,
    Self,
}

internal enum RemovePasskeyOutcome
{
    Removed,
    NotFound,
    PasskeyRequired,
}

/// <summary>Admins and their passkeys. Every admin keeps at least one passkey, and one admin always remains.</summary>
internal sealed class Admins(IMongoDatabase database, AdminSessions sessions, TimeProvider time)
{
    private readonly IMongoCollection<AdminUser> admins = AdminUser.In(database);
    private readonly IMongoCollection<AdminPasskeyDocument> passkeys = AdminPasskeyDocument.In(database);

    public async Task<AdminUser?> FindAsync(string adminId, CancellationToken cancellationToken) =>
        await admins.Find(admin => admin.Id == adminId).FirstOrDefaultAsync(cancellationToken);

    public Task<List<AdminUser>> ListAsync(CancellationToken cancellationToken) =>
        admins.Find(_ => true).SortBy(admin => admin.CreatedAt).ThenBy(admin => admin.Id).ToListAsync(cancellationToken);

    public Task<List<AdminPasskeyDocument>> PasskeysAsync(IEnumerable<string> adminIds, CancellationToken cancellationToken) =>
        passkeys.Find(Builders<AdminPasskeyDocument>.Filter.In(passkey => passkey.AdminId, adminIds)).SortBy(passkey => passkey.CreatedAt).ToListAsync(cancellationToken);

    public async Task<int> PasskeyCountAsync(string adminId, CancellationToken cancellationToken) =>
        (int)await passkeys.CountDocumentsAsync(passkey => passkey.AdminId == adminId, cancellationToken: cancellationToken);

    public AdminUser New(string displayName) => new()
    {
        Id = AdminUser.NewId(),
        DisplayName = displayName,
        CreatedAt = time.GetUtcNow().UtcDateTime,
    };

    /// <summary>Stores a new admin with their first passkey; false, with nothing stored, when the passkey is registered already.</summary>
    public async Task<bool> CreateAsync(AdminUser admin, AdminPasskeyDocument passkey, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(admin);
        await admins.InsertOneAsync(admin, cancellationToken: cancellationToken);
        if (await AddPasskeyAsync(passkey, cancellationToken))
        {
            return true;
        }

        await admins.DeleteOneAsync(candidate => candidate.Id == admin.Id, cancellationToken);
        return false;
    }

    /// <summary>False when the passkey is registered already.</summary>
    public async Task<bool> AddPasskeyAsync(AdminPasskeyDocument passkey, CancellationToken cancellationToken)
    {
        try
        {
            await passkeys.InsertOneAsync(passkey, cancellationToken: cancellationToken);
            return true;
        }
        catch (MongoWriteException e) when (e.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            return false;
        }
    }

    public Task RecordUseAsync(string passkeyId, long signCount, bool isBackedUp, CancellationToken cancellationToken) =>
        passkeys.UpdateOneAsync(passkey => passkey.Id == passkeyId,
            Builders<AdminPasskeyDocument>.Update.Set(passkey => passkey.SignCount, signCount).Set(passkey => passkey.IsBackedUp, isBackedUp)
                .Set(passkey => passkey.LastUsedAt, time.GetUtcNow().UtcDateTime),
            cancellationToken: cancellationToken);

    /// <summary>Removes another admin with their passkeys and ends their sessions; never the last admin and never the caller.</summary>
    public async Task<(RemoveAdminOutcome Outcome, AdminUser? Removed)> RemoveAsync(string callerId, string adminId, CancellationToken cancellationToken)
    {
        var admin = await FindAsync(adminId, cancellationToken);
        if (admin is null)
        {
            return (RemoveAdminOutcome.NotFound, null);
        }

        if (await admins.CountDocumentsAsync(_ => true, cancellationToken: cancellationToken) <= 1)
        {
            return (RemoveAdminOutcome.LastAdmin, admin);
        }

        if (adminId == callerId)
        {
            return (RemoveAdminOutcome.Self, admin);
        }

        if ((await admins.DeleteOneAsync(candidate => candidate.Id == adminId, cancellationToken)).DeletedCount == 0)
        {
            return (RemoveAdminOutcome.NotFound, null);
        }

        // Two admins removing each other at once could both pass the count; the one that empties the collection undoes it.
        if (await admins.CountDocumentsAsync(_ => true, cancellationToken: cancellationToken) == 0)
        {
            await admins.InsertOneAsync(admin, cancellationToken: cancellationToken);
            return (RemoveAdminOutcome.LastAdmin, admin);
        }

        await sessions.EndAllAsync(adminId, cancellationToken);
        await passkeys.DeleteManyAsync(passkey => passkey.AdminId == adminId, cancellationToken);
        return (RemoveAdminOutcome.Removed, admin);
    }

    /// <summary>Removes one passkey of an admin unless it is their last one.</summary>
    public async Task<RemovePasskeyOutcome> RemovePasskeyAsync(string adminId, string passkeyId, CancellationToken cancellationToken)
    {
        var passkey = await passkeys.Find(candidate => candidate.Id == passkeyId && candidate.AdminId == adminId).FirstOrDefaultAsync(cancellationToken);
        if (passkey is null)
        {
            return RemovePasskeyOutcome.NotFound;
        }

        if (await PasskeyCountAsync(adminId, cancellationToken) <= 1)
        {
            return RemovePasskeyOutcome.PasskeyRequired;
        }

        if ((await passkeys.DeleteOneAsync(candidate => candidate.Id == passkeyId && candidate.AdminId == adminId, cancellationToken)).DeletedCount == 0)
        {
            return RemovePasskeyOutcome.NotFound;
        }

        // Concurrent removals can each pass the count; the one that removed the last passkey puts it back.
        if (await PasskeyCountAsync(adminId, cancellationToken) == 0)
        {
            await passkeys.InsertOneAsync(passkey, cancellationToken: cancellationToken);
            return RemovePasskeyOutcome.PasskeyRequired;
        }

        return RemovePasskeyOutcome.Removed;
    }
}

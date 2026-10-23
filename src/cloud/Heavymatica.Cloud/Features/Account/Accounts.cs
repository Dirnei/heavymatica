using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Account;

internal sealed class QuotaOptions
{
    public const string Section = "Storage";

    public long DefaultQuotaBytes { get; set; } = 100L * 1024 * 1024;
}

internal sealed record SignedInIdentity(AccountDocument Account, IdentityDocument Identity)
{
    public AuditActor Actor => new(Identity.Provider, Identity.Id);
}

internal enum AddIdentityOutcome
{
    Added,
    AlreadyLinked,
    TooManyIdentities,
}

internal sealed class Accounts(IMongoDatabase database, AccountAudit audit, TimeProvider time, IOptions<QuotaOptions> quota)
{
    public const int MaxIdentities = 10;

    private readonly IMongoCollection<AccountDocument> accounts = AccountDocument.In(database);
    private readonly IMongoCollection<IdentityDocument> identities = IdentityDocument.In(database);

    public Task<AccountDocument?> FindAsync(string accountId, CancellationToken cancellationToken) =>
        accounts.Find(account => account.Id == accountId).FirstOrDefaultAsync(cancellationToken)!;

    public Task<IdentityDocument?> FindIdentityAsync(string identityId, CancellationToken cancellationToken) =>
        identities.Find(identity => identity.Id == identityId).FirstOrDefaultAsync(cancellationToken)!;

    public async Task<SignedInIdentity?> FindByIdentityAsync(string provider, string subject, CancellationToken cancellationToken)
    {
        var identity = await identities.Find(candidate => candidate.Provider == provider && candidate.Subject == subject).FirstOrDefaultAsync(cancellationToken);
        return identity is null ? null : await WithAccountAsync(identity, cancellationToken);
    }

    /// <summary>The account linked to the identity, with the identity's name and last sign-in updated; null when none is linked.</summary>
    public async Task<SignedInIdentity?> SignInAsync(string provider, string subject, string name, CancellationToken cancellationToken)
    {
        var identity = await identities.FindOneAndUpdateAsync(
            Builders<IdentityDocument>.Filter.Eq(candidate => candidate.Provider, provider) & Builders<IdentityDocument>.Filter.Eq(candidate => candidate.Subject, subject),
            Builders<IdentityDocument>.Update.Set(candidate => candidate.Name, name).Set(candidate => candidate.LastSignInAt, time.GetUtcNow().UtcDateTime),
            new FindOneAndUpdateOptions<IdentityDocument> { ReturnDocument = ReturnDocument.After },
            cancellationToken);
        return identity is null ? null : await WithAccountAsync(identity, cancellationToken);
    }

    /// <summary>
    /// A new account holding the identity as its only one. When the identity is already linked, including by a
    /// concurrent call, nothing is created and the linked account is returned with <c>Created</c> false.
    /// </summary>
    public async Task<(SignedInIdentity SignedIn, bool Created)> CreateWithIdentityAsync(string provider, string subject, string? name,
        CancellationToken cancellationToken)
    {
        if (await SignInAsync(provider, subject, name ?? subject, cancellationToken) is { } existing)
        {
            return (existing, false);
        }

        var now = time.GetUtcNow();
        var account = new AccountDocument
        {
            Id = AccountDocument.NewId(),
            UsedBytes = 0,
            QuotaBytes = quota.Value.DefaultQuotaBytes,
            CreatedAt = now,
        };
        await accounts.InsertOneAsync(account, cancellationToken: cancellationToken);

        var identity = NewIdentity(account.Id, provider, subject, name, now.UtcDateTime);
        try
        {
            await identities.InsertOneAsync(identity, cancellationToken: cancellationToken);
        }
        catch (MongoWriteException e) when (e.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            await accounts.DeleteOneAsync(candidate => candidate.Id == account.Id, cancellationToken);
            var winner = await FindByIdentityAsync(provider, subject, cancellationToken)
                ?? throw new InvalidOperationException($"The {provider} identity {subject} is linked to an account that does not exist.", e);
            return (winner, false);
        }

        var signedIn = new SignedInIdentity(account, identity);
        await audit.RecordAsync(account.Id, AuditKinds.AccountCreated, signedIn.Actor, cancellationToken);
        return (signedIn, true);
    }

    public async Task<AddIdentityOutcome> AddIdentityAsync(string accountId, string provider, string subject, string? name, AuditActor actor,
        CancellationToken cancellationToken)
    {
        // A race can reach one more than the limit; the limit is a sanity bound, not a security boundary.
        if (await identities.CountDocumentsAsync(identity => identity.AccountId == accountId, cancellationToken: cancellationToken) >= MaxIdentities)
        {
            return AddIdentityOutcome.TooManyIdentities;
        }

        var identity = NewIdentity(accountId, provider, subject, name, time.GetUtcNow().UtcDateTime);
        try
        {
            await identities.InsertOneAsync(identity, cancellationToken: cancellationToken);
        }
        catch (MongoWriteException e) when (e.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            return AddIdentityOutcome.AlreadyLinked;
        }

        await audit.RecordAsync(accountId, AuditKinds.IdentityAdded, actor, cancellationToken, identity.Id);
        return AddIdentityOutcome.Added;
    }

    public async Task<bool> RemoveIdentityAsync(string accountId, string identityId, AuditActor actor, CancellationToken cancellationToken)
    {
        var removed = await identities.DeleteOneAsync(identity => identity.Id == identityId && identity.AccountId == accountId, cancellationToken);
        if (removed.DeletedCount == 0)
        {
            return false;
        }

        await audit.RecordAsync(accountId, AuditKinds.IdentityRemoved, actor, cancellationToken, identityId);
        return true;
    }

    public static Contract.Account ToContract(AccountDocument account, IdentityDocument identity) => new()
    {
        Id = account.Id,
        Provider = identity.Provider,
        Uuid = identity.Provider == Providers.Minecraft && Guid.TryParse(identity.Subject, out var uuid) ? uuid : null,
        Name = identity.Name ?? identity.Subject,
        UsedBytes = account.UsedBytes,
        QuotaBytes = account.QuotaBytes,
    };

    public static IdentityDocument NewIdentity(string accountId, string provider, string subject, string? name, DateTime now) => new()
    {
        Id = AccountDocument.NewId(),
        AccountId = accountId,
        Provider = provider,
        Subject = subject,
        Name = name,
        LinkedAt = now,
        LastSignInAt = now,
    };

    private async Task<SignedInIdentity?> WithAccountAsync(IdentityDocument identity, CancellationToken cancellationToken) =>
        await FindAsync(identity.AccountId, cancellationToken) is { } account ? new SignedInIdentity(account, identity) : null;
}

using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Schematics;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>Player accounts as admins see them: lookup, overview and quota. Never schematic content.</summary>
internal sealed class AccountsApi(IMongoDatabase database, AdminAudit audit, IHttpContextAccessor http) : IAdminAccountsController
{
    public const int MaxResults = 50;
    public const int AuditEntries = 20;
    public const long MaxQuotaBytes = 100L * 1024 * 1024 * 1024;

    private readonly IMongoCollection<AccountDocument> accounts = AccountDocument.In(database);
    private readonly IMongoCollection<IdentityDocument> identities = IdentityDocument.In(database);

    private HttpContext Context => http.HttpContext!;

    public async Task<ActionResult<AccountSearch>> FindAccountsAsync(string q, CancellationToken cancellationToken = default)
    {
        var query = q?.Trim() ?? string.Empty;
        if (query.Length is 0 or > 64)
        {
            return AdminProblems.Invalid("Search for an account ID, a Minecraft UUID or a name.");
        }

        var accountIds = new List<string>();
        if (query.Length == 32 && query.All(Uri.IsHexDigit))
        {
            accountIds.Add(query.ToLowerInvariant());
        }

        if (Guid.TryParse(query, out var uuid))
        {
            var subject = uuid.ToString("D");
            accountIds.AddRange(await identities.Find(identity => identity.Provider == Providers.Minecraft && identity.Subject == subject)
                .Project(identity => identity.AccountId).ToListAsync(cancellationToken));
        }
        else
        {
            var lower = Accounts.LowerName(query);
            accountIds.AddRange(await identities.Find(identity => identity.NameLower == lower)
                .SortBy(identity => identity.LinkedAt).Project(identity => identity.AccountId).Limit(MaxResults * 2).ToListAsync(cancellationToken));
        }

        var ids = accountIds.Distinct(StringComparer.Ordinal).Take(MaxResults).ToList();
        var found = await accounts.Find(Builders<AccountDocument>.Filter.In(account => account.Id, ids)).ToListAsync(cancellationToken);
        var linked = (await identities.Find(Builders<IdentityDocument>.Filter.In(identity => identity.AccountId, found.Select(account => account.Id)))
            .SortBy(identity => identity.LinkedAt).ToListAsync(cancellationToken)).ToLookup(identity => identity.AccountId);
        return new AccountSearch
        {
            Accounts = found.OrderBy(account => ids.IndexOf(account.Id)).Select(account => new AccountSummary
            {
                Id = account.Id,
                CreatedAt = account.CreatedAt,
                Identities = linked[account.Id].Select(Identity).ToList(),
                UsedBytes = account.UsedBytes,
                QuotaBytes = account.QuotaBytes,
            }).ToList(),
        };
    }

    public async Task<ActionResult<AccountOverview>> GetAccountAsync(string accountId, CancellationToken cancellationToken = default)
    {
        var account = await accounts.Find(candidate => candidate.Id == accountId).FirstOrDefaultAsync(cancellationToken);
        return account is null ? AdminProblems.NotFound("There is no such account.") : await OverviewAsync(account, cancellationToken);
    }

    public async Task<ActionResult<AccountOverview>> SetQuotaAsync(QuotaChange body, string accountId, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        var reason = body.Reason?.Trim() ?? string.Empty;
        if (reason.Length is < 3 or > 200)
        {
            return AdminProblems.Invalid("A reason of 3 to 200 characters is required.");
        }

        if (body.QuotaBytes is < 0 or > MaxQuotaBytes)
        {
            return AdminProblems.Invalid("The quota is 0 to 100 GiB.");
        }

        var before = await accounts.FindOneAndUpdateAsync(
            Builders<AccountDocument>.Filter.Eq(account => account.Id, accountId),
            Builders<AccountDocument>.Update.Set(account => account.QuotaBytes, body.QuotaBytes),
            new FindOneAndUpdateOptions<AccountDocument> { ReturnDocument = ReturnDocument.Before },
            cancellationToken);
        if (before is null)
        {
            return AdminProblems.NotFound("There is no such account.");
        }

        await audit.RecordAsync(AdminActor.Of(AdminSessions.Caller(Context).Admin, AdminContract.Ip(Context)), AdminAuditKinds.QuotaChanged, accountId,
            new()
            {
                ["accountId"] = accountId,
                ["oldBytes"] = before.QuotaBytes.ToString(System.Globalization.CultureInfo.InvariantCulture),
                ["newBytes"] = body.QuotaBytes.ToString(System.Globalization.CultureInfo.InvariantCulture),
                ["reason"] = reason,
            }, cancellationToken);
        before.QuotaBytes = body.QuotaBytes;
        return await OverviewAsync(before, cancellationToken);
    }

    private async Task<AccountOverview> OverviewAsync(AccountDocument account, CancellationToken cancellationToken)
    {
        var linked = await identities.Find(identity => identity.AccountId == account.Id).SortBy(identity => identity.LinkedAt).ToListAsync(cancellationToken);
        var schematics = await SchematicDocument.In(database).CountDocumentsAsync(schematic => schematic.Owner == account.Id, cancellationToken: cancellationToken);
        var entries = await AuditEntryDocument.In(database).Find(entry => entry.AccountId == account.Id)
            .SortByDescending(entry => entry.At).ThenByDescending(entry => entry.Id).Limit(AuditEntries).ToListAsync(cancellationToken);
        var subjects = linked.ToDictionary(identity => identity.Id, identity => identity.Subject);
        return new AccountOverview
        {
            Id = account.Id,
            CreatedAt = account.CreatedAt,
            Identities = linked.Select(Identity).ToList(),
            UsedBytes = account.UsedBytes,
            QuotaBytes = account.QuotaBytes,
            SchematicCount = schematics,
            Audit = entries.Select(entry => new AccountAuditEntry
            {
                At = AdminContract.Utc(entry.At),
                Kind = entry.Kind,
                IdentityId = entry.IdentityId,
                Actor = entry.Actor.Provider + ":" + (entry.Actor.Subject
                    ?? (entry.Actor.IdentityId is { } identityId ? subjects.GetValueOrDefault(identityId, identityId) : string.Empty)),
            }).ToList(),
        };
    }

    private static AccountIdentity Identity(IdentityDocument identity) => new()
    {
        Id = identity.Id,
        Provider = identity.Provider,
        Subject = identity.Subject,
        Name = identity.Name,
        LinkedAt = AdminContract.Utc(identity.LinkedAt),
        LastSignInAt = AdminContract.Utc(identity.LastSignInAt),
    };
}

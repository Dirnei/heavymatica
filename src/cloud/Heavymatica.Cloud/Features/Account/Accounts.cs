using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Account;

/// <summary>Configuration section <c>Storage</c> values the account needs.</summary>
internal sealed class QuotaOptions
{
    public const string Section = "Storage";

    /// <summary>Quota of a new account; 100 MiB unless configured.</summary>
    public long DefaultQuotaBytes { get; set; } = 100L * 1024 * 1024;
}

/// <summary>Accounts, keyed by Minecraft UUID.</summary>
internal sealed class Accounts(IMongoDatabase database, TimeProvider time, IOptions<QuotaOptions> quota)
{
    private readonly IMongoCollection<AccountDocument> accounts = AccountDocument.In(database);

    /// <summary>Creates the account on the first sign-in of a UUID; later sign-ins only refresh the name.</summary>
    public Task<AccountDocument> SignedInAsync(Guid uuid, string name, CancellationToken cancellationToken) =>
        accounts.FindOneAndUpdateAsync(
            Builders<AccountDocument>.Filter.Eq(account => account.Id, Key(uuid)),
            Builders<AccountDocument>.Update
                .Set(account => account.Name, name)
                .SetOnInsert(account => account.UsedBytes, 0)
                .SetOnInsert(account => account.QuotaBytes, quota.Value.DefaultQuotaBytes)
                .SetOnInsert(account => account.CreatedAt, time.GetUtcNow()),
            new FindOneAndUpdateOptions<AccountDocument> { IsUpsert = true, ReturnDocument = ReturnDocument.After },
            cancellationToken);

    public Task<AccountDocument?> FindAsync(string accountId, CancellationToken cancellationToken) =>
        accounts.Find(account => account.Id == accountId).FirstOrDefaultAsync(cancellationToken)!;

    public static string Key(Guid uuid) => uuid.ToString("D");

    public static Contract.Account ToContract(AccountDocument account) => new()
    {
        Uuid = Guid.Parse(account.Id),
        Name = account.Name,
        UsedBytes = account.UsedBytes,
        QuotaBytes = account.QuotaBytes,
    };
}

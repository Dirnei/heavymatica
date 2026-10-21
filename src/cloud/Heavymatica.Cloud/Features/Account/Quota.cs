using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Account;

/// <summary>
/// Storage quota: every stored version and thumbnail counts toward <c>usedBytes</c>. Reservations are a single
/// conditional increment, so concurrent uploads can never take an account over its quota (D5).
/// </summary>
internal sealed class Quota(IMongoDatabase database)
{
    private readonly IMongoCollection<AccountDocument> accounts = AccountDocument.In(database);

    /// <summary>Adds <paramref name="bytes"/> to the account's usage if it stays within the quota.</summary>
    public async Task<bool> TryReserveAsync(string accountId, long bytes, CancellationToken cancellationToken)
    {
        var fits = new BsonDocument("$expr", new BsonDocument("$lte", new BsonArray
        {
            new BsonDocument("$add", new BsonArray { "$usedBytes", bytes }),
            "$quotaBytes",
        }));
        var filter = Builders<AccountDocument>.Filter.Eq(account => account.Id, accountId) & fits;
        var result = await accounts.UpdateOneAsync(filter, Builders<AccountDocument>.Update.Inc(account => account.UsedBytes, bytes),
            cancellationToken: cancellationToken);
        return result.ModifiedCount == 1;
    }

    public Task ReleaseAsync(string accountId, long bytes, CancellationToken cancellationToken) =>
        bytes == 0
            ? Task.CompletedTask
            : accounts.UpdateOneAsync(account => account.Id == accountId, Builders<AccountDocument>.Update.Inc(account => account.UsedBytes, -bytes),
                cancellationToken: cancellationToken);
}

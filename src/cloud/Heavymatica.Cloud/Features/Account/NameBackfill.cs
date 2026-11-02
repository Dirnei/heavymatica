using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Account;

/// <summary>
/// Gives identities stored before names were looked up case-insensitively their lower-case name. Running it again
/// finds nothing to do.
/// </summary>
internal sealed partial class NameBackfill(IMongoDatabase database, ILogger<NameBackfill> logger) : IHostedService
{
    public async Task StartAsync(CancellationToken cancellationToken)
    {
        var filter = Builders<IdentityDocument>.Filter;
        var result = await IdentityDocument.In(database).UpdateManyAsync(
            filter.Type(identity => identity.Name, BsonType.String) & filter.Exists(identity => identity.NameLower, false),
            new PipelineUpdateDefinition<IdentityDocument>(new[]
            {
                new BsonDocument("$set", new BsonDocument("nameLower", new BsonDocument("$toLower", "$name"))),
            }),
            cancellationToken: cancellationToken);
        if (result.ModifiedCount > 0)
        {
            Backfilled(logger, result.ModifiedCount);
        }
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;

    [LoggerMessage(Level = LogLevel.Information, Message = "Stored the lower-case name of {Count} identities")]
    private static partial void Backfilled(ILogger logger, long count);
}

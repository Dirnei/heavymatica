using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Thumbnails;

/// <summary>
/// Queues every version that has no thumbnail and was never queued, due at its upload time, so older versions sort
/// behind new uploads. Versions stored before rendering existed also lack the attempt count, which the queue's
/// <c>renderAttempts &lt; n</c> filter needs to match them. Running it again finds nothing to do.
/// </summary>
internal sealed partial class RenderBackfill(IMongoDatabase database, ILogger<RenderBackfill> logger) : IHostedService
{
    public async Task StartAsync(CancellationToken cancellationToken)
    {
        var filter = Builders<VersionDocument>.Filter;
        var result = await VersionDocument.In(database).UpdateManyAsync(
            filter.Eq(version => version.HasThumbnail, false)
                & (filter.Exists(version => version.ThumbnailDueAt, false) | filter.Exists(version => version.RenderAttempts, false)),
            new PipelineUpdateDefinition<VersionDocument>(new[]
            {
                new BsonDocument("$set", new BsonDocument
                {
                    { "thumbnailDueAt", new BsonDocument("$ifNull", new BsonArray { "$thumbnailDueAt", "$createdAt" }) },
                    { "renderAttempts", new BsonDocument("$ifNull", new BsonArray { "$renderAttempts", 0 }) },
                }),
            }),
            cancellationToken: cancellationToken);
        if (result.ModifiedCount > 0)
        {
            Queued(logger, result.ModifiedCount);
        }
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;

    [LoggerMessage(Level = LogLevel.Information, Message = "Queued {Count} versions without a thumbnail for rendering")]
    private static partial void Queued(ILogger logger, long count);
}

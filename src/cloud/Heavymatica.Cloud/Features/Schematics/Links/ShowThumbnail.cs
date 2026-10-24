using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Links;

/// <summary>
/// Redirects to a thumbnail's short-lived link, so pages can use it as an image source. The redirect is cached for a
/// little less than the link lives, so the browser keeps reusing one link and its cached image.
/// </summary>
internal sealed class ShowThumbnail(IMongoDatabase database, BlobStore blobs, TimeProvider time, IOptions<StorageOptions> options)
{
    private static readonly TimeSpan CacheMargin = TimeSpan.FromMinutes(1);

    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);

    public async Task<ActionResult<Problem>> ShowAsync(HttpResponse response, string owner, string schematicId, int number,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(response);
        var version = await versions.Find(document => document.Id == VersionDocument.KeyOf(schematicId, number) && document.Owner == owner)
            .FirstOrDefaultAsync(cancellationToken);
        if (version is not { HasThumbnail: true })
        {
            return SchematicProblems.NotFound();
        }

        var lifetime = options.Value.LinkLifetime;
        var link = await blobs.LinkAsync(BlobStore.ThumbnailKey(owner, schematicId, number), time.GetUtcNow() + lifetime);
        var cacheSeconds = (int)Math.Max(0, (lifetime - CacheMargin).TotalSeconds);
        response.Headers.CacheControl = $"private, max-age={cacheSeconds}";
        return new RedirectResult(link);
    }
}

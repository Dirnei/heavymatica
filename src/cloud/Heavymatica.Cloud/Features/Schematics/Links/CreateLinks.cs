using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Links;

/// <summary>Short-lived download links for a version's file and thumbnail; the bytes never pass through the API.</summary>
internal sealed class CreateLinks(IMongoDatabase database, BlobStore blobs, TimeProvider time, IOptions<StorageOptions> options)
{
    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);

    public async Task<ActionResult<DownloadLinks>> CreateAsync(string owner, string schematicId, int number, CancellationToken cancellationToken)
    {
        var version = await versions.Find(document => document.Id == VersionDocument.KeyOf(schematicId, number) && document.Owner == owner)
            .FirstOrDefaultAsync(cancellationToken);
        if (version is null)
        {
            return SchematicProblems.NotFound();
        }

        var expiresAt = time.GetUtcNow() + options.Value.LinkLifetime;
        return new DownloadLinks
        {
            FileUrl = new Uri(await blobs.LinkAsync(BlobStore.FileKey(owner, version.Sha256), expiresAt)),
            ThumbnailUrl = version.HasThumbnail ? new Uri(await blobs.LinkAsync(BlobStore.ThumbnailKey(owner, schematicId, number), expiresAt)) : null,
            ExpiresAt = expiresAt,
        };
    }
}

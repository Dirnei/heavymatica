using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Delete;

/// <summary>
/// Deletes a whole schematic or a single version. Usage is released at once; a file blob is removed when no other
/// version of the account still refers to it (D5), so issued links stop working no later than their expiry.
/// </summary>
internal sealed class DeleteSchematics(IMongoDatabase database, Quota quota, BlobStore blobs)
{
    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);
    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);

    public async Task<IActionResult> DeleteSchematicAsync(string owner, string schematicId, CancellationToken cancellationToken)
    {
        var schematic = await schematics.FindOneAndDeleteAsync(document => document.Id == schematicId && document.Owner == owner, cancellationToken: cancellationToken);
        if (schematic is null)
        {
            return SchematicProblems.NotFound();
        }

        var removed = await versions.Find(version => version.SchematicId == schematicId).ToListAsync(cancellationToken);
        await versions.DeleteManyAsync(version => version.SchematicId == schematicId, cancellationToken);
        await quota.ReleaseAsync(owner, removed.Sum(version => version.SizeBytes + version.ThumbnailBytes), CancellationToken.None);
        await RemoveBlobsAsync(owner, removed, cancellationToken);
        return new NoContentResult();
    }

    public async Task<IActionResult> DeleteVersionAsync(string owner, string schematicId, int number, CancellationToken cancellationToken)
    {
        var schematic = await schematics.Find(document => document.Id == schematicId && document.Owner == owner).FirstOrDefaultAsync(cancellationToken);
        if (schematic is null || !await versions.Find(document => document.Id == VersionDocument.KeyOf(schematicId, number)).AnyAsync(cancellationToken))
        {
            return SchematicProblems.NotFound();
        }

        if (await versions.CountDocumentsAsync(version => version.SchematicId == schematicId, cancellationToken: cancellationToken) <= 1)
        {
            return Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Last_version,
                "This is the schematic's only version. Delete the schematic instead.");
        }

        var removed = await versions.FindOneAndDeleteAsync(document => document.Id == VersionDocument.KeyOf(schematicId, number), cancellationToken: cancellationToken);
        if (removed is null)
        {
            return SchematicProblems.NotFound();
        }

        await quota.ReleaseAsync(owner, removed.SizeBytes + removed.ThumbnailBytes, CancellationToken.None);
        if (schematic.Latest.Number == number)
        {
            var latest = await versions.Find(version => version.SchematicId == schematicId).SortByDescending(version => version.Number).FirstAsync(cancellationToken);
            await schematics.UpdateOneAsync(document => document.Id == schematicId,
                Builders<SchematicDocument>.Update.Set(document => document.Latest, latest), cancellationToken: cancellationToken);
        }

        await RemoveBlobsAsync(owner, [removed], cancellationToken);
        return new NoContentResult();
    }

    private async Task RemoveBlobsAsync(string owner, IEnumerable<VersionDocument> removed, CancellationToken cancellationToken)
    {
        foreach (var version in removed)
        {
            if (version.HasThumbnail)
            {
                await blobs.DeleteAsync(BlobStore.ThumbnailKey(owner, version.SchematicId, version.Number), cancellationToken);
            }
        }

        foreach (var sha256 in removed.Select(version => version.Sha256).Distinct(StringComparer.Ordinal))
        {
            if (!await versions.Find(version => version.Owner == owner && version.Sha256 == sha256).AnyAsync(cancellationToken))
            {
                await blobs.DeleteAsync(BlobStore.FileKey(owner, sha256), cancellationToken);
            }
        }
    }
}

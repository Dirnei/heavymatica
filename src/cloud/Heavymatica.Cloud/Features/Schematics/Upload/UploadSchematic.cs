using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Formats;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Upload;

/// <summary>
/// Uploads a schematic or a new version (cloud/schematic-storage, D5). The order makes failures invisible: quota is
/// reserved before anything is stored and released on any later failure, the blob is stored before the version
/// document, and the version only becomes visible once both exist. A failure leaves at most an orphan blob for the
/// sweeper.
/// </summary>
internal sealed class UploadSchematic(UploadSpool spool, IMongoDatabase database, Quota quota, BlobStore blobs, TimeProvider time)
{
    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);
    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);

    public async Task<ActionResult<UploadResult>> NewAsync(string owner, string? name, Stream body, CancellationToken cancellationToken)
    {
        var trimmed = name?.Trim();
        if (string.IsNullOrEmpty(trimmed) || trimmed.Length > 100)
        {
            return Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Invalid_request, "A name of 1 to 100 characters is required.");
        }

        return await StoreAsync(owner, body, existing: null, async (upload, model, ct) =>
        {
            var schematicId = ObjectId.GenerateNewId().ToString();
            var version = Version(owner, schematicId, 1, upload, model);
            await versions.InsertOneAsync(version, cancellationToken: ct);
            try
            {
                await schematics.InsertOneAsync(new SchematicDocument
                {
                    Id = schematicId,
                    Owner = owner,
                    Name = trimmed,
                    NameLower = trimmed.ToLowerInvariant(),
                    NextVersion = 2,
                    Latest = version,
                    UpdatedAt = version.CreatedAt,
                }, cancellationToken: ct);
            }
            catch
            {
                await versions.DeleteOneAsync(document => document.Id == version.Id, CancellationToken.None);
                throw;
            }

            return Created(schematicId, version);
        }, cancellationToken);
    }

    public async Task<ActionResult<UploadResult>> VersionAsync(string owner, string schematicId, Stream body, CancellationToken cancellationToken)
    {
        var schematic = await schematics.Find(document => document.Id == schematicId && document.Owner == owner).FirstOrDefaultAsync(cancellationToken);
        if (schematic is null)
        {
            return SchematicProblems.NotFound();
        }

        return await StoreAsync(owner, body, schematic, async (upload, model, ct) =>
        {
            var claimed = await schematics.FindOneAndUpdateAsync(
                document => document.Id == schematicId && document.Owner == owner,
                Builders<SchematicDocument>.Update.Inc(document => document.NextVersion, 1),
                new FindOneAndUpdateOptions<SchematicDocument> { ReturnDocument = ReturnDocument.Before },
                ct);
            if (claimed is null)
            {
                throw new SchematicDeletedException();
            }

            var version = Version(owner, schematicId, claimed.NextVersion, upload, model);
            await versions.InsertOneAsync(version, cancellationToken: ct);
            await schematics.UpdateOneAsync(
                Builders<SchematicDocument>.Filter.Eq(document => document.Id, schematicId)
                    & Builders<SchematicDocument>.Filter.Lt(document => document.Latest.Number, version.Number),
                Builders<SchematicDocument>.Update.Set(document => document.Latest, version).Set(document => document.UpdatedAt, version.CreatedAt),
                cancellationToken: ct);
            return Created(schematicId, version);
        }, cancellationToken);
    }

    private async Task<ActionResult<UploadResult>> StoreAsync(
        string owner,
        Stream body,
        SchematicDocument? existing,
        Func<SpooledUpload, SchematicModel, CancellationToken, Task<ActionResult<UploadResult>>> record,
        CancellationToken cancellationToken)
    {
        SpooledUpload upload;
        try
        {
            upload = await spool.SpoolAsync(body, SchematicLimits.MaxFileBytes, cancellationToken);
        }
        catch (SchematicRejectedException rejected)
        {
            return SchematicProblems.Rejected(rejected);
        }

        using (upload)
        {
            // Identical to the latest version: nothing to store and no version number used up.
            if (existing is not null && existing.Latest.Sha256 == upload.Sha256)
            {
                return new OkObjectResult(new UploadResult { SchematicId = existing.Id, Version = existing.Latest.ToContract() });
            }

            SchematicModel model;
            try
            {
                model = SchematicReader.Read(upload.Path);
            }
            catch (SchematicRejectedException rejected)
            {
                return SchematicProblems.Rejected(rejected);
            }

            if (!await quota.TryReserveAsync(owner, upload.Size, cancellationToken))
            {
                return Problems.Result(StatusCodes.Status422UnprocessableEntity, ProblemCode.Over_quota,
                    "This upload would exceed your storage quota. Delete schematics or versions to free space.");
            }

            try
            {
                var key = BlobStore.FileKey(owner, upload.Sha256);
                if (!await blobs.ExistsAsync(key, cancellationToken))
                {
                    await blobs.PutFileAsync(key, upload.Path, "application/octet-stream", cancellationToken);
                }

                return await record(upload, model, cancellationToken);
            }
            catch (SchematicDeletedException)
            {
                await quota.ReleaseAsync(owner, upload.Size, CancellationToken.None);
                return SchematicProblems.NotFound();
            }
            catch
            {
                await quota.ReleaseAsync(owner, upload.Size, CancellationToken.None);
                throw;
            }
        }
    }

    private VersionDocument Version(string owner, string schematicId, int number, SpooledUpload upload, SchematicModel model)
    {
        var counts = model.BlockCounts();
        return new VersionDocument
        {
            Id = VersionDocument.KeyOf(schematicId, number),
            SchematicId = schematicId,
            Owner = owner,
            Number = number,
            Sha256 = upload.Sha256,
            Format = model.Format,
            SizeBytes = upload.Size,
            Width = model.Width,
            Height = model.Height,
            Length = model.Length,
            DataVersion = model.DataVersion,
            BlockTotal = counts.Sum(count => count.Value),
            BlockCounts = counts.Select(count => new BlockCountDocument(count.Key, count.Value)).ToList(),
            SkippedEntities = model.SkippedEntities,
            // Millisecond precision, as MongoDB stores it, so the answer matches what later reads return.
            CreatedAt = DateTime.UnixEpoch.AddMilliseconds(time.GetUtcNow().ToUnixTimeMilliseconds()),
        };
    }

    private static ObjectResult Created(string schematicId, VersionDocument version) =>
        new(new UploadResult { SchematicId = schematicId, Version = version.ToContract() }) { StatusCode = StatusCodes.Status201Created };

    /// <summary>The schematic was deleted while a new version was being uploaded.</summary>
    private sealed class SchematicDeletedException : Exception;
}

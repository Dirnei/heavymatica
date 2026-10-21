using System.Buffers.Binary;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Thumbnails;

/// <summary>
/// Attaches a version's thumbnail once: a PNG of at most 256 x 256 pixels and 256 KiB, counted toward the quota.
/// Only the PNG header is checked; the image itself is never decoded on the server.
/// </summary>
internal sealed class AttachThumbnail(IMongoDatabase database, Quota quota, BlobStore blobs)
{
    private static readonly byte[] PngSignature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];

    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);
    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);

    public async Task<IActionResult> AttachAsync(string owner, string schematicId, int number, Stream body, CancellationToken cancellationToken)
    {
        var key = VersionDocument.KeyOf(schematicId, number);
        var version = await versions.Find(document => document.Id == key && document.Owner == owner).FirstOrDefaultAsync(cancellationToken);
        if (version is null)
        {
            return SchematicProblems.NotFound();
        }

        if (version.HasThumbnail)
        {
            return Exists();
        }

        var png = await ReadAtMostAsync(body, StorageOptions.MaxThumbnailBytes + 1, cancellationToken);
        if (png.Length > StorageOptions.MaxThumbnailBytes)
        {
            return Problems.Result(StatusCodes.Status413PayloadTooLarge, ProblemCode.Thumbnail_invalid, "A thumbnail has at most 256 KiB.");
        }

        if (Dimensions(png) is not var (width, height))
        {
            return Problems.Result(StatusCodes.Status422UnprocessableEntity, ProblemCode.Thumbnail_invalid, "The thumbnail is not a PNG.");
        }

        if (width > StorageOptions.MaxThumbnailPixels || height > StorageOptions.MaxThumbnailPixels)
        {
            return Problems.Result(StatusCodes.Status422UnprocessableEntity, ProblemCode.Thumbnail_invalid, "A thumbnail has at most 256 x 256 pixels.");
        }

        if (!await quota.TryReserveAsync(owner, png.Length, cancellationToken))
        {
            return Problems.Result(StatusCodes.Status422UnprocessableEntity, ProblemCode.Over_quota, "The thumbnail would exceed your storage quota.");
        }

        try
        {
            await blobs.PutBytesAsync(BlobStore.ThumbnailKey(owner, schematicId, number), png, "image/png", cancellationToken);
            var attached = await versions.UpdateOneAsync(
                document => document.Id == key && document.Owner == owner && !document.HasThumbnail,
                Builders<VersionDocument>.Update.Set(document => document.HasThumbnail, true).Set(document => document.ThumbnailBytes, png.Length),
                cancellationToken: cancellationToken);
            if (attached.ModifiedCount == 0)
            {
                // Another request attached one first; keep its bytes and accounting.
                await quota.ReleaseAsync(owner, png.Length, CancellationToken.None);
                return Exists();
            }

            await schematics.UpdateOneAsync(
                Builders<SchematicDocument>.Filter.Eq(document => document.Id, schematicId) & Builders<SchematicDocument>.Filter.Eq(document => document.Latest.Number, number),
                Builders<SchematicDocument>.Update.Set(document => document.Latest.HasThumbnail, true).Set(document => document.Latest.ThumbnailBytes, png.Length),
                cancellationToken: cancellationToken);
            return new NoContentResult();
        }
        catch
        {
            await quota.ReleaseAsync(owner, png.Length, CancellationToken.None);
            throw;
        }
    }

    /// <summary>Width and height from the IHDR chunk, or null when the bytes are not a PNG.</summary>
    internal static (int Width, int Height)? Dimensions(ReadOnlySpan<byte> png)
    {
        // Signature (8), IHDR length (4) = 13, "IHDR" (4), width (4), height (4).
        if (png.Length < 24 || !png[..8].SequenceEqual(PngSignature) || BinaryPrimitives.ReadInt32BigEndian(png[8..]) != 13
            || !png[12..16].SequenceEqual("IHDR"u8))
        {
            return null;
        }

        var width = BinaryPrimitives.ReadInt32BigEndian(png[16..]);
        var height = BinaryPrimitives.ReadInt32BigEndian(png[20..]);
        return width > 0 && height > 0 ? (width, height) : null;
    }

    private static async Task<byte[]> ReadAtMostAsync(Stream body, int limit, CancellationToken cancellationToken)
    {
        using var collected = new MemoryStream();
        var buffer = new byte[16 * 1024];
        int read;
        while (collected.Length < limit && (read = await body.ReadAsync(buffer.AsMemory(0, (int)Math.Min(buffer.Length, limit - collected.Length)), cancellationToken)) > 0)
        {
            collected.Write(buffer, 0, read);
        }

        return collected.ToArray();
    }

    private static ObjectResult Exists() =>
        Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Thumbnail_exists, "This version already has a thumbnail.");
}

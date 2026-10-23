using System.Security.Cryptography;
using System.Text;
using Amazon.Runtime;
using Amazon.S3;
using Amazon.S3.Model;
using Heavymatica.Cloud.Features.Schematics;
using Heavymatica.Cloud.Formats;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Heavymatica.Cloud.Infrastructure.S3;
using Heavymatica.Cloud.Tests.Features.Schematics;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Account;

/// <summary>
/// Seeds a test database and bucket in the shape the service stored before accounts had identities: accounts keyed
/// by the dashed Minecraft UUID, owners and session accounts holding that UUID, and files under <c>accounts/{uuid}/</c>.
/// </summary>
internal sealed class LegacyData : IDisposable
{
    private readonly AmazonS3Client s3;

    public LegacyData(CloudInfrastructure infrastructure, string databaseName)
    {
        MongoServices.RegisterConventions();
        DatabaseName = databaseName;
        Bucket = CloudFactory.BucketFor(databaseName);
        Database = new MongoClient(infrastructure.MongoConnectionStringFor(databaseName)).GetDatabase(databaseName);
        s3 = new AmazonS3Client(new BasicAWSCredentials(infrastructure.MinioUser, infrastructure.MinioPassword), new AmazonS3Config
        {
            ServiceURL = infrastructure.MinioEndpoint.ToString(),
            ForcePathStyle = true,
            AuthenticationRegion = "us-east-1",
            RequestChecksumCalculation = RequestChecksumCalculation.WHEN_REQUIRED,
            ResponseChecksumValidation = ResponseChecksumValidation.WHEN_REQUIRED,
        });
    }

    public string DatabaseName { get; }

    public string Bucket { get; }

    public IMongoDatabase Database { get; }

    public void Dispose() => s3.Dispose();

    /// <summary>
    /// A legacy account with one schematic per entry of <paramref name="versionsPerSchematic"/>, that many versions
    /// each, and thumbnails on the first <paramref name="thumbnails"/> versions. Usage is the sum of every stored size.
    /// </summary>
    public async Task<LegacyAccount> AccountAsync(string name, int[] versionsPerSchematic, int thumbnails, long quotaBytes,
        CancellationToken cancellationToken)
    {
        await EnsureBucketAsync(cancellationToken);
        var uuid = Guid.NewGuid().ToString("D");
        var fixtures = new[] { "house.nbt", "house.v2.schem", "house.v3.schem", "stairs.v2.schem", "modded.v3.schem", "entities.nbt" };
        var used = 0L;
        var schematicIds = new List<string>();
        var fixture = 0;
        var thumbnailsLeft = thumbnails;
        foreach (var count in versionsPerSchematic)
        {
            var schematicId = ObjectId.GenerateNewId().ToString();
            schematicIds.Add(schematicId);
            VersionDocument? latest = null;
            for (var number = 1; number <= count; number++)
            {
                var bytes = Library.Fixture(fixtures[fixture++ % fixtures.Length]);
                var sha = Convert.ToHexStringLower(SHA256.HashData(bytes));
                await PutAsync(BlobStore.FileKey(uuid, sha), bytes, cancellationToken);
                var thumbnail = thumbnailsLeft-- > 0 ? Library.Png(16, 16, 100 + number) : null;
                if (thumbnail is not null)
                {
                    await PutAsync(BlobStore.ThumbnailKey(uuid, schematicId, number), thumbnail, cancellationToken);
                }

                latest = new VersionDocument
                {
                    Id = VersionDocument.KeyOf(schematicId, number),
                    SchematicId = schematicId,
                    Owner = uuid,
                    Number = number,
                    Sha256 = sha,
                    Format = SchematicFormat.Litematica,
                    SizeBytes = bytes.Length,
                    Width = 2,
                    Height = 3,
                    Length = 4,
                    DataVersion = 4189,
                    BlockTotal = 3,
                    BlockCounts = [new BlockCountDocument("minecraft:stone", 2), new BlockCountDocument("minecraft:glass", 1)],
                    HasThumbnail = thumbnail is not null,
                    ThumbnailBytes = thumbnail?.Length ?? 0,
                    CreatedAt = DateTime.UnixEpoch.AddMilliseconds(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()),
                };
                used += bytes.Length + (thumbnail?.Length ?? 0);
                await VersionDocument.In(Database).InsertOneAsync(latest, cancellationToken: cancellationToken);
            }

            await SchematicDocument.In(Database).InsertOneAsync(new SchematicDocument
            {
                Id = schematicId,
                Owner = uuid,
                Name = $"{name} build {schematicIds.Count}",
                NameLower = $"{name} build {schematicIds.Count}".ToLowerInvariant(),
                NextVersion = count + 1,
                Latest = latest!,
                UpdatedAt = latest!.CreatedAt,
            }, cancellationToken: cancellationToken);
        }

        await Database.GetCollection<BsonDocument>("accounts").InsertOneAsync(new BsonDocument
        {
            ["_id"] = uuid,
            ["name"] = name,
            ["usedBytes"] = used,
            ["quotaBytes"] = quotaBytes,
            ["createdAt"] = new BsonArray { DateTimeOffset.UtcNow.UtcTicks, 0 },
        }, cancellationToken: cancellationToken);

        var sessionId = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(16));
        var refreshToken = sessionId + "." + Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
        var now = DateTime.UnixEpoch.AddMilliseconds(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
        await Database.GetCollection<BsonDocument>("sessions").InsertOneAsync(new BsonDocument
        {
            ["_id"] = sessionId,
            ["accountId"] = uuid,
            ["currentRefreshHash"] = Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(refreshToken))),
            ["revoked"] = false,
            ["createdAt"] = now,
            ["lastUsedAt"] = now,
        }, cancellationToken: cancellationToken);

        return new LegacyAccount(Guid.Parse(uuid), name, schematicIds, versionsPerSchematic.Sum(), used, quotaBytes, sessionId, refreshToken);
    }

    public async Task<List<string>> KeysAsync(string prefix, CancellationToken cancellationToken)
    {
        var keys = new List<string>();
        string? continuation = null;
        do
        {
            var page = await s3.ListObjectsV2Async(new ListObjectsV2Request { BucketName = Bucket, Prefix = prefix, ContinuationToken = continuation },
                cancellationToken);
            keys.AddRange((page.S3Objects ?? []).Select(item => item.Key));
            continuation = page.IsTruncated == true ? page.NextContinuationToken : null;
        }
        while (continuation is not null);

        return keys;
    }

    private async Task EnsureBucketAsync(CancellationToken cancellationToken)
    {
        try
        {
            await s3.PutBucketAsync(new PutBucketRequest { BucketName = Bucket }, cancellationToken);
        }
        catch (AmazonS3Exception e) when (e.ErrorCode is "BucketAlreadyOwnedByYou" or "BucketAlreadyExists")
        {
        }
    }

    private async Task PutAsync(string key, byte[] content, CancellationToken cancellationToken)
    {
        using var stream = new MemoryStream(content, writable: false);
        await s3.PutObjectAsync(new PutObjectRequest { BucketName = Bucket, Key = key, InputStream = stream }, cancellationToken);
    }
}

internal sealed record LegacyAccount(
    Guid Uuid,
    string Name,
    List<string> SchematicIds,
    int Versions,
    long UsedBytes,
    long QuotaBytes,
    string SessionId,
    string RefreshToken)
{
    public string Key => Uuid.ToString("D");
}

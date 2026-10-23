using System.Diagnostics;
using Amazon.Runtime;
using Amazon.S3;
using Amazon.S3.Model;
using Heavymatica.Cloud.Features.Account;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Account;

/// <summary>
/// Runs the migration on a copy of real data. Opt-in: <c>HEAVYMATICA_MIGRATION_SOURCE</c> is the MongoDB connection
/// string of the source database (with the database name), <c>HEAVYMATICA_MIGRATION_S3</c> the source S3 endpoint.
/// <c>HEAVYMATICA_MIGRATION_S3_BUCKET</c>, <c>_S3_ACCESS_KEY</c> and <c>_S3_SECRET_KEY</c> default to the dev AppHost's
/// values. The source is only read.
/// </summary>
[Trait("Category", "Integration")]
public sealed class MigrationRehearsalTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task The_migration_keeps_every_library_of_a_copy_of_real_data()
    {
        var source = Environment.GetEnvironmentVariable("HEAVYMATICA_MIGRATION_SOURCE");
        var sourceS3 = Environment.GetEnvironmentVariable("HEAVYMATICA_MIGRATION_S3");
        Assert.SkipWhen(string.IsNullOrWhiteSpace(source) || string.IsNullOrWhiteSpace(sourceS3),
            "Set HEAVYMATICA_MIGRATION_SOURCE and HEAVYMATICA_MIGRATION_S3 to rehearse the migration on a copy of real data.");

        var sourceUrl = MongoUrl.Create(source);
        var sourceDatabase = new MongoClient(sourceUrl).GetDatabase(sourceUrl.DatabaseName ?? "heavymatica");
        using var sourceBlobs = S3(new Uri(sourceS3!),
            Setting("HEAVYMATICA_MIGRATION_S3_ACCESS_KEY", "heavymatica"), Setting("HEAVYMATICA_MIGRATION_S3_SECRET_KEY", "heavymatica-dev-secret"));
        var sourceBucket = Setting("HEAVYMATICA_MIGRATION_S3_BUCKET", "heavymatica");

        var scratch = new LegacyData(infrastructure, CloudFactory.NewDatabaseName());
        disposables.Add(scratch);
        using var scratchBlobs = S3(infrastructure.MinioEndpoint, infrastructure.MinioUser, infrastructure.MinioPassword);
        await CopyDatabaseAsync(sourceDatabase, scratch.Database);
        var copiedObjects = await MirrorBucketAsync(sourceBlobs, sourceBucket, scratchBlobs, scratch.Bucket);

        var before = await CountsAsync(scratch, account => account);
        Assert.NotEmpty(before);

        var watch = Stopwatch.StartNew();
        var factory = new CloudFactory(infrastructure, scratch.DatabaseName);
        disposables.Add(factory);
        _ = factory.Services;
        watch.Stop();

        var migrated = (await MigrationDocument.In(scratch.Database).Find(migration => migration.Id != MigrationDocument.LockId).ToListAsync(Cancel))
            .ToDictionary(migration => migration.Id, migration => migration.AccountId!);
        var after = await CountsAsync(scratch, uuid => migrated[uuid], before.Keys);
        TestContext.Current.TestOutputHelper?.WriteLine(
            $"Migrated {before.Count} accounts with {copiedObjects} stored objects in {watch.Elapsed.TotalSeconds:F2} s (including service start).");

        Assert.Equal(before.Keys.Order(), migrated.Keys.Order());
        foreach (var (uuid, counts) in before)
        {
            Assert.Equal(counts, after[uuid]);
        }

        Assert.Equal(0, await scratch.Database.GetCollection<BsonDocument>(AccountDocument.Collection)
            .CountDocumentsAsync(Builders<BsonDocument>.Filter.Regex("_id", new BsonRegularExpression("-")), cancellationToken: Cancel));
    }

    private static async Task<Dictionary<string, Counts>> CountsAsync(LegacyData data, Func<string, string> owner, IEnumerable<string>? uuids = null)
    {
        uuids ??= await data.Database.GetCollection<BsonDocument>(AccountDocument.Collection)
            .Find(Builders<BsonDocument>.Filter.Regex("_id", new BsonRegularExpression("^[0-9a-f]{8}-")))
            .Project(account => account["_id"].AsString).ToListAsync(Cancel);
        var counts = new Dictionary<string, Counts>(StringComparer.Ordinal);
        foreach (var uuid in uuids)
        {
            var accountId = owner(uuid);
            var account = await data.Database.GetCollection<BsonDocument>(AccountDocument.Collection)
                .Find(new BsonDocument("_id", accountId)).SingleAsync(Cancel);
            var byOwner = new BsonDocument("owner", accountId);
            counts[uuid] = new Counts(
                await data.Database.GetCollection<BsonDocument>("schematics").CountDocumentsAsync(byOwner, cancellationToken: Cancel),
                await data.Database.GetCollection<BsonDocument>("versions").CountDocumentsAsync(byOwner, cancellationToken: Cancel),
                (await data.KeysAsync($"accounts/{accountId}/", Cancel)).Count,
                account["usedBytes"].ToInt64(),
                account["quotaBytes"].ToInt64());
        }

        return counts;
    }

    private static async Task CopyDatabaseAsync(IMongoDatabase source, IMongoDatabase target)
    {
        foreach (var name in await (await source.ListCollectionNamesAsync(cancellationToken: Cancel)).ToListAsync(Cancel))
        {
            if (name.StartsWith("system.", StringComparison.Ordinal))
            {
                continue;
            }

            var documents = await source.GetCollection<BsonDocument>(name).Find(_ => true).ToListAsync(Cancel);
            if (documents.Count > 0)
            {
                await target.GetCollection<BsonDocument>(name).InsertManyAsync(documents, cancellationToken: Cancel);
            }
        }
    }

    private static async Task<int> MirrorBucketAsync(AmazonS3Client source, string sourceBucket, AmazonS3Client target, string targetBucket)
    {
        try
        {
            await target.PutBucketAsync(new PutBucketRequest { BucketName = targetBucket }, Cancel);
        }
        catch (AmazonS3Exception e) when (e.ErrorCode is "BucketAlreadyOwnedByYou" or "BucketAlreadyExists")
        {
        }

        var copied = 0;
        string? continuation = null;
        do
        {
            var page = await source.ListObjectsV2Async(new ListObjectsV2Request { BucketName = sourceBucket, Prefix = "accounts/", ContinuationToken = continuation }, Cancel);
            foreach (var item in page.S3Objects ?? [])
            {
                using var read = await source.GetObjectAsync(sourceBucket, item.Key, Cancel);
                using var buffer = new MemoryStream();
                await read.ResponseStream.CopyToAsync(buffer, Cancel);
                buffer.Position = 0;
                await target.PutObjectAsync(new PutObjectRequest { BucketName = targetBucket, Key = item.Key, InputStream = buffer }, Cancel);
                copied++;
            }

            continuation = page.IsTruncated == true ? page.NextContinuationToken : null;
        }
        while (continuation is not null);

        return copied;
    }

    private static AmazonS3Client S3(Uri endpoint, string accessKey, string secretKey) =>
        new(new BasicAWSCredentials(accessKey, secretKey), new AmazonS3Config
        {
            ServiceURL = endpoint.ToString(),
            ForcePathStyle = true,
            AuthenticationRegion = "us-east-1",
            RequestChecksumCalculation = RequestChecksumCalculation.WHEN_REQUIRED,
            ResponseChecksumValidation = ResponseChecksumValidation.WHEN_REQUIRED,
        });

    private static string Setting(string name, string fallback) =>
        Environment.GetEnvironmentVariable(name) is { Length: > 0 } value ? value : fallback;

    private sealed record Counts(long Schematics, long Versions, int Blobs, long UsedBytes, long QuotaBytes);
}

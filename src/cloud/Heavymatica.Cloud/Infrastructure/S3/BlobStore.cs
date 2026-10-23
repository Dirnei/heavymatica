using System.Net;
using Amazon.Runtime;
using Amazon.S3;
using Amazon.S3.Model;
using Microsoft.Extensions.Diagnostics.HealthChecks;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Infrastructure.S3;

internal sealed class S3Options
{
    public const string Section = "S3";

    /// <summary>Endpoint of an S3-compatible store; empty means AWS S3 in <see cref="Region"/>.</summary>
    public Uri? ServiceUrl { get; set; }

    public string Region { get; set; } = "us-east-1";

    public string AccessKey { get; set; } = string.Empty;

    public string SecretKey { get; set; } = string.Empty;

    public string Bucket { get; set; } = "heavymatica";
}

internal sealed record StoredBlob(string Key, long Size, DateTimeOffset LastModified);

internal sealed class BlobStore(IAmazonS3 s3, IOptions<S3Options> options)
{
    private string Bucket => options.Value.Bucket;

    public static string FileKey(string accountId, string sha256) => $"accounts/{accountId}/blobs/{sha256}";

    public static string ThumbnailKey(string accountId, string schematicId, int version) => $"accounts/{accountId}/thumbnails/{schematicId}/{version}.png";

    public async Task<bool> ExistsAsync(string key, CancellationToken cancellationToken)
    {
        try
        {
            await s3.GetObjectMetadataAsync(Bucket, key, cancellationToken);
            return true;
        }
        catch (AmazonS3Exception e) when (e.StatusCode == HttpStatusCode.NotFound)
        {
            return false;
        }
    }

    public Task PutFileAsync(string key, string path, string contentType, CancellationToken cancellationToken) =>
        s3.PutObjectAsync(new PutObjectRequest { BucketName = Bucket, Key = key, FilePath = path, ContentType = contentType }, cancellationToken);

    public async Task PutBytesAsync(string key, byte[] content, string contentType, CancellationToken cancellationToken)
    {
        using var stream = new MemoryStream(content, writable: false);
        await s3.PutObjectAsync(new PutObjectRequest { BucketName = Bucket, Key = key, InputStream = stream, ContentType = contentType }, cancellationToken);
    }

    public Task DeleteAsync(string key, CancellationToken cancellationToken) => s3.DeleteObjectAsync(Bucket, key, cancellationToken);

    /// <summary>A server-side copy within the bucket.</summary>
    public Task CopyAsync(string sourceKey, string targetKey, CancellationToken cancellationToken) =>
        s3.CopyObjectAsync(new CopyObjectRequest
        {
            SourceBucket = Bucket,
            SourceKey = sourceKey,
            DestinationBucket = Bucket,
            DestinationKey = targetKey,
        }, cancellationToken);

    /// <summary>A link that downloads the object without credentials until <paramref name="expiresAt"/>.</summary>
    public Task<string> LinkAsync(string key, DateTimeOffset expiresAt) =>
        s3.GetPreSignedURLAsync(new GetPreSignedUrlRequest
        {
            BucketName = Bucket,
            Key = key,
            Verb = HttpVerb.GET,
            Expires = expiresAt.UtcDateTime,
            Protocol = options.Value.ServiceUrl?.Scheme == "http" ? Protocol.HTTP : Protocol.HTTPS,
        });

    public async IAsyncEnumerable<StoredBlob> ListAsync(string prefix, [System.Runtime.CompilerServices.EnumeratorCancellation] CancellationToken cancellationToken)
    {
        string? continuation = null;
        do
        {
            var page = await s3.ListObjectsV2Async(new ListObjectsV2Request { BucketName = Bucket, Prefix = prefix, ContinuationToken = continuation }, cancellationToken);
            foreach (var item in page.S3Objects ?? [])
            {
                yield return new StoredBlob(item.Key, item.Size ?? 0, new DateTimeOffset(item.LastModified ?? DateTime.MinValue, TimeSpan.Zero));
            }

            continuation = page.IsTruncated == true ? page.NextContinuationToken : null;
        }
        while (continuation is not null);
    }

    public async Task EnsureBucketAsync(CancellationToken cancellationToken)
    {
        try
        {
            await s3.PutBucketAsync(new PutBucketRequest { BucketName = Bucket }, cancellationToken);
        }
        catch (AmazonS3Exception e) when (e.ErrorCode is "BucketAlreadyOwnedByYou" or "BucketAlreadyExists")
        {
        }
    }
}

internal static class S3Services
{
    public static WebApplicationBuilder AddBlobStore(this WebApplicationBuilder builder)
    {
        builder.Services.AddOptions<S3Options>().BindConfiguration(S3Options.Section);
        builder.Services.AddSingleton<IAmazonS3>(provider =>
        {
            var options = provider.GetRequiredService<IOptions<S3Options>>().Value;
            var config = new AmazonS3Config
            {
                AuthenticationRegion = options.Region,
                RequestChecksumCalculation = RequestChecksumCalculation.WHEN_REQUIRED,
                ResponseChecksumValidation = ResponseChecksumValidation.WHEN_REQUIRED,
            };
            if (options.ServiceUrl is not null)
            {
                config.ServiceURL = options.ServiceUrl.ToString();
                config.ForcePathStyle = true;
            }
            else
            {
                config.RegionEndpoint = Amazon.RegionEndpoint.GetBySystemName(options.Region);
            }

            return new AmazonS3Client(new BasicAWSCredentials(options.AccessKey, options.SecretKey), config);
        });
        builder.Services.AddSingleton<BlobStore>();
        builder.Services.AddHostedService<BucketSetup>();
        builder.Services.AddHealthChecks().AddCheck<BlobStoreHealthCheck>("s3");
        return builder;
    }
}

/// <summary>Creates the bucket when it does not exist yet (local development and tests).</summary>
internal sealed class BucketSetup(BlobStore blobs) : IHostedService
{
    public Task StartAsync(CancellationToken cancellationToken) => blobs.EnsureBucketAsync(cancellationToken);

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;
}

internal sealed class BlobStoreHealthCheck(BlobStore blobs) : IHealthCheck
{
    public async Task<HealthCheckResult> CheckHealthAsync(HealthCheckContext context, CancellationToken cancellationToken = default)
    {
        try
        {
            await blobs.ExistsAsync("health-check", cancellationToken);
            return HealthCheckResult.Healthy();
        }
        catch (Exception e) when (e is AmazonServiceException or HttpRequestException)
        {
            return HealthCheckResult.Unhealthy("The blob store is unreachable.", e);
        }
    }
}

using Akka.Actor;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Formats;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Thumbnails;

internal enum RenderOutcome
{
    /// <summary>No version is due.</summary>
    Idle,

    Rendered,

    /// <summary>The version holds nothing drawable; it is not tried again.</summary>
    NothingToDraw,

    /// <summary>An attached thumbnail or a deletion won while rendering; the rendered image was thrown away.</summary>
    Superseded,

    /// <summary>Parsing or drawing failed; the version is tried again until it runs out of attempts.</summary>
    Failed,
}

/// <summary>
/// Renders thumbnails for versions that have none: claims the newest due version, draws it, stores the PNG at the
/// rendered key and marks the version, unless an attached thumbnail got there first. Safe with several instances.
/// </summary>
internal sealed partial class ThumbnailWorker(
    IMongoDatabase database,
    BlobStore blobs,
    Quota quota,
    TimeProvider time,
    IOptions<ThumbnailOptions> options,
    ILogger<ThumbnailWorker> logger)
{
    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);
    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);

    private ThumbnailOptions Options => options.Value;

    public async Task<RenderOutcome> RenderNextAsync(CancellationToken cancellationToken)
    {
        var version = await ClaimAsync(cancellationToken);
        if (version is null)
        {
            return RenderOutcome.Idle;
        }

        byte[]? png;
        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(Options.RenderTimeout);
            png = await RenderAsync(version, timeout.Token);
        }
        catch (Exception error) when (error is not OperationCanceledException || !cancellationToken.IsCancellationRequested)
        {
            RenderFailed(logger, version.Id, version.RenderAttempts, error);
            return RenderOutcome.Failed;
        }

        if (png is null)
        {
            await versions.UpdateOneAsync(document => document.Id == version.Id,
                Builders<VersionDocument>.Update.Set(document => document.RenderAttempts, Options.MaxAttempts).Unset(document => document.RenderClaimedUntil),
                cancellationToken: cancellationToken);
            return RenderOutcome.NothingToDraw;
        }

        return await StoreAsync(version, png, cancellationToken);
    }

    private Task<VersionDocument?> ClaimAsync(CancellationToken cancellationToken)
    {
        var now = time.GetUtcNow().UtcDateTime;
        var filter = Builders<VersionDocument>.Filter;
        var due = filter.Eq(document => document.HasThumbnail, false)
            & filter.Lte(document => document.ThumbnailDueAt, now)
            & filter.Lt(document => document.RenderAttempts, Options.MaxAttempts)
            & (filter.Eq(document => document.RenderClaimedUntil, null) | filter.Lte(document => document.RenderClaimedUntil, now));
        return versions.FindOneAndUpdateAsync(
            due,
            Builders<VersionDocument>.Update.Inc(document => document.RenderAttempts, 1).Set(document => document.RenderClaimedUntil, now + Options.ClaimTime),
            new FindOneAndUpdateOptions<VersionDocument>
            {
                Sort = Builders<VersionDocument>.Sort.Descending(document => document.ThumbnailDueAt),
                ReturnDocument = ReturnDocument.After,
            },
            cancellationToken)!;
    }

    private async Task<byte[]?> RenderAsync(VersionDocument version, CancellationToken cancellationToken)
    {
        var file = await blobs.GetBytesAsync(BlobStore.FileKey(version.Owner, version.Sha256), cancellationToken);
        return await Task.Run(() =>
        {
            var model = SchematicReader.Read(file);
            cancellationToken.ThrowIfCancellationRequested();
            var pixels = IsometricRasterizer.Render(model, BlockColors.Embedded, Options.MaxDrawnBlocks);
            return pixels is null ? null : PngEncoder.Encode(pixels, IsometricRasterizer.Size, IsometricRasterizer.Size);
        }, cancellationToken);
    }

    private async Task<RenderOutcome> StoreAsync(VersionDocument version, byte[] png, CancellationToken cancellationToken)
    {
        var key = BlobStore.ThumbnailKey(version.Owner, version.SchematicId, version.Number, rendered: true);
        await blobs.PutBytesAsync(key, png, "image/png", cancellationToken);
        var marked = await versions.UpdateOneAsync(
            document => document.Id == version.Id && !document.HasThumbnail,
            Builders<VersionDocument>.Update
                .Set(document => document.HasThumbnail, true)
                .Set(document => document.ThumbnailSource, ThumbnailOrigin.Rendered)
                .Set(document => document.ThumbnailBytes, png.Length)
                .Unset(document => document.RenderClaimedUntil),
            cancellationToken: cancellationToken);
        if (marked.ModifiedCount == 0)
        {
            await blobs.DeleteAsync(key, CancellationToken.None);
            return RenderOutcome.Superseded;
        }

        await quota.ChargeAsync(version.Owner, png.Length, CancellationToken.None);
        await schematics.UpdateOneAsync(
            Builders<SchematicDocument>.Filter.Eq(document => document.Id, version.SchematicId)
                & Builders<SchematicDocument>.Filter.Eq(document => document.Latest.Number, version.Number)
                & Builders<SchematicDocument>.Filter.Eq(document => document.Latest.HasThumbnail, false),
            Builders<SchematicDocument>.Update
                .Set(document => document.Latest.HasThumbnail, true)
                .Set(document => document.Latest.ThumbnailSource, ThumbnailOrigin.Rendered)
                .Set(document => document.Latest.ThumbnailBytes, png.Length),
            cancellationToken: CancellationToken.None);
        return RenderOutcome.Rendered;
    }

    [LoggerMessage(Level = LogLevel.Warning, Message = "Rendering the thumbnail of {VersionId} failed (attempt {Attempt})")]
    private static partial void RenderFailed(ILogger logger, string versionId, int attempt, Exception error);
}

/// <summary>
/// Runs the <see cref="ThumbnailWorker"/> one version at a time: on a timer while idle, and straight on while there is
/// work, so the backfill keeps going without slowing new uploads.
/// </summary>
internal sealed partial class ThumbnailWorkerActor : ReceiveActor, IWithTimers
{
    private readonly ThumbnailWorker worker;
    private bool running;

    public ThumbnailWorkerActor(ThumbnailWorker worker, IOptions<ThumbnailOptions> options, ILogger<ThumbnailWorkerActor> logger)
    {
        this.worker = worker;
        Receive<Tick>(_ => Start());
        Receive<RenderOutcome>(outcome =>
        {
            running = false;
            if (outcome != RenderOutcome.Idle)
            {
                Start();
            }
        });
        Receive<Status.Failure>(failure =>
        {
            running = false;
            WorkerFailed(logger, failure.Cause);
        });
        if (options.Value.Enabled)
        {
            Timers!.StartPeriodicTimer("render", Tick.Instance, options.Value.PollInterval, options.Value.PollInterval);
        }
    }

    public ITimerScheduler Timers { get; set; } = null!;

    private void Start()
    {
        if (running)
        {
            return;
        }

        running = true;
        worker.RenderNextAsync(CancellationToken.None).PipeTo(Self);
    }

    [LoggerMessage(Level = LogLevel.Error, Message = "The thumbnail worker failed")]
    private static partial void WorkerFailed(ILogger logger, Exception error);

    private sealed class Tick
    {
        public static readonly Tick Instance = new();
    }
}

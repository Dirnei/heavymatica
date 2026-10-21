using Akka.Actor;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Sweeper;

/// <summary>
/// Removes blobs no version refers to: leftovers of failed uploads and anything a deletion missed. Only blobs older
/// than <see cref="StorageOptions.OrphanAge"/> are touched, so an upload in flight never loses its blob (D5).
/// </summary>
internal sealed class BlobSweeper(IMongoDatabase database, BlobStore blobs, TimeProvider time, IOptions<StorageOptions> options)
{
    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);

    /// <summary>Sweeps once and returns how many blobs were deleted.</summary>
    public async Task<int> SweepAsync(CancellationToken cancellationToken)
    {
        var cutoff = time.GetUtcNow() - options.Value.OrphanAge;
        var deleted = 0;
        await foreach (var blob in blobs.ListAsync("accounts/", cancellationToken))
        {
            if (blob.LastModified <= cutoff && !await IsReferencedAsync(blob.Key, cancellationToken))
            {
                await blobs.DeleteAsync(blob.Key, cancellationToken);
                deleted++;
            }
        }

        return deleted;
    }

    private Task<bool> IsReferencedAsync(string key, CancellationToken cancellationToken)
    {
        // accounts/{owner}/blobs/{sha256} or accounts/{owner}/thumbnails/{schematicId}/{number}.png
        var parts = key.Split('/');
        if (parts is ["accounts", var owner, "blobs", var sha256])
        {
            return versions.Find(version => version.Owner == owner && version.Sha256 == sha256).AnyAsync(cancellationToken);
        }

        if (parts is ["accounts", var thumbnailOwner, "thumbnails", var schematicId, var file]
            && file.EndsWith(".png", StringComparison.Ordinal) && int.TryParse(file[..^4], out var number))
        {
            var versionKey = VersionDocument.KeyOf(schematicId, number);
            return versions.Find(version => version.Id == versionKey && version.Owner == thumbnailOwner && version.HasThumbnail).AnyAsync(cancellationToken);
        }

        // Keys the service does not know are left alone.
        return Task.FromResult(true);
    }
}

/// <summary>Runs the <see cref="BlobSweeper"/> on a timer, one sweep at a time.</summary>
internal sealed class BlobSweeperActor : ReceiveActor, IWithTimers
{
    /// <summary>Sweep now; the sender gets the number of deleted blobs.</summary>
    public sealed record Sweep;

    private sealed record Swept(int Deleted, IActorRef? ReplyTo);

    private sealed record Failed(Exception Error, IActorRef? ReplyTo);

    private readonly BlobSweeper sweeper;
    private bool running;

    public BlobSweeperActor(BlobSweeper sweeper, IOptions<StorageOptions> options)
    {
        this.sweeper = sweeper;
        var interval = options.Value.SweepInterval;
        Receive<Sweep>(_ => Start(Sender));
        Receive<Tick>(_ => Start(null));
        Receive<Swept>(swept =>
        {
            running = false;
            swept.ReplyTo?.Tell(swept.Deleted);
        });
        Receive<Failed>(failed =>
        {
            running = false;
            failed.ReplyTo?.Tell(new Status.Failure(failed.Error));
        });
        Timers!.StartPeriodicTimer("sweep", Tick.Instance, interval, interval);
    }

    public ITimerScheduler Timers { get; set; } = null!;

    private void Start(IActorRef? replyTo)
    {
        if (running)
        {
            replyTo?.Tell(0);
            return;
        }

        running = true;
        sweeper.SweepAsync(CancellationToken.None).PipeTo(Self, success: deleted => new Swept(deleted, replyTo), failure: error => new Failed(error, replyTo));
    }

    private sealed class Tick
    {
        public static readonly Tick Instance = new();
    }
}

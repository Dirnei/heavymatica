using System.Diagnostics.CodeAnalysis;
using System.Threading.RateLimiting;

namespace Heavymatica.Cloud.Features.Links;

/// <summary>
/// A fixed-window limiter that reads the service clock. The framework's fixed-window limiter measures time with its
/// own stopwatch, so its windows could not be moved by a test clock.
/// </summary>
internal sealed class ClockedWindowLimiter(TimeProvider time, int permitLimit, TimeSpan window) : RateLimiter
{
    private readonly Lock gate = new();
    private DateTimeOffset windowStart = time.GetUtcNow();
    private int used;

    /// <summary>Idle once the current window has passed, so the partitioned limiter may drop it; a new one starts empty.</summary>
    public override TimeSpan? IdleDuration
    {
        get
        {
            lock (gate)
            {
                var idle = time.GetUtcNow() - (windowStart + window);
                return idle > TimeSpan.Zero ? idle : null;
            }
        }
    }

    public override RateLimiterStatistics? GetStatistics()
    {
        lock (gate)
        {
            Roll();
            return new RateLimiterStatistics { CurrentAvailablePermits = permitLimit - used };
        }
    }

    protected override RateLimitLease AttemptAcquireCore(int permitCount)
    {
        lock (gate)
        {
            Roll();
            if (used + permitCount > permitLimit)
            {
                return Lease.Failed;
            }

            used += permitCount;
            return Lease.Succeeded;
        }
    }

    protected override ValueTask<RateLimitLease> AcquireAsyncCore(int permitCount, CancellationToken cancellationToken) =>
        ValueTask.FromResult(AttemptAcquireCore(permitCount));

    private void Roll()
    {
        var now = time.GetUtcNow();
        if (now >= windowStart + window)
        {
            windowStart = now;
            used = 0;
        }
    }

    private sealed class Lease(bool acquired) : RateLimitLease
    {
        public static readonly Lease Succeeded = new(true);
        public static readonly Lease Failed = new(false);

        public override bool IsAcquired { get; } = acquired;

        public override IEnumerable<string> MetadataNames => [];

        public override bool TryGetMetadata(string metadataName, [NotNullWhen(true)] out object? metadata)
        {
            metadata = null;
            return false;
        }
    }
}

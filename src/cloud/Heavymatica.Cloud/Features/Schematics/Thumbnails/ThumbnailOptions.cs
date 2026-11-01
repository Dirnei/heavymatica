namespace Heavymatica.Cloud.Features.Schematics.Thumbnails;

/// <summary>The service's thumbnail rendering, configured under <c>Thumbnails</c>.</summary>
internal sealed class ThumbnailOptions
{
    public const string Section = "Thumbnails";

    /// <summary>The render queue's index only holds versions below this many attempts.</summary>
    public const int DefaultMaxAttempts = 3;

    public bool Enabled { get; set; } = true;

    /// <summary>How long a version uploaded from the game waits for the game to attach its own thumbnail.</summary>
    public TimeSpan GracePeriod { get; set; } = TimeSpan.FromMinutes(1);

    public TimeSpan PollInterval { get; set; } = TimeSpan.FromSeconds(5);

    /// <summary>How long a claimed version is left to one renderer before another may try.</summary>
    public TimeSpan ClaimTime { get; set; } = TimeSpan.FromMinutes(5);

    public TimeSpan RenderTimeout { get; set; } = TimeSpan.FromSeconds(30);

    public int MaxAttempts { get; set; } = DefaultMaxAttempts;

    public int MaxDrawnBlocks { get; set; } = 2_000_000;
}

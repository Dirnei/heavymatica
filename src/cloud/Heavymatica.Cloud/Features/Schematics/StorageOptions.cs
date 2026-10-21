namespace Heavymatica.Cloud.Features.Schematics;

internal sealed class StorageOptions
{
    public const string Section = "Storage";

    public TimeSpan LinkLifetime { get; set; } = TimeSpan.FromMinutes(5);

    /// <summary>Blobs no version refers to are deleted once they are older than this.</summary>
    public TimeSpan OrphanAge { get; set; } = TimeSpan.FromHours(1);

    public TimeSpan SweepInterval { get; set; } = TimeSpan.FromHours(1);

    public const int MaxThumbnailBytes = 256 * 1024;
    public const int MaxThumbnailPixels = 256;
}

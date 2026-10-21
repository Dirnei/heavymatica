namespace Heavymatica.Cloud.Features.Schematics;

/// <summary>Configuration section <c>Storage</c> values the schematic slices use.</summary>
internal sealed class StorageOptions
{
    public const string Section = "Storage";

    /// <summary>How long download links work; 5 minutes per cloud/schematic-storage.</summary>
    public TimeSpan LinkLifetime { get; set; } = TimeSpan.FromMinutes(5);

    /// <summary>Blobs no version refers to are deleted once they are older than this.</summary>
    public TimeSpan OrphanAge { get; set; } = TimeSpan.FromHours(1);

    /// <summary>How often the sweeper looks for orphan blobs.</summary>
    public TimeSpan SweepInterval { get; set; } = TimeSpan.FromHours(1);

    public const int MaxThumbnailBytes = 256 * 1024;
    public const int MaxThumbnailPixels = 256;
}

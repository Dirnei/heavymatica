using Heavymatica.Cloud.Contract;

namespace Heavymatica.Cloud.Features.Schematics;

/// <summary>Documents to contract DTOs.</summary>
internal static class SchematicMapping
{
    public static Contract.Version ToContract(this VersionDocument version) => new()
    {
        Number = version.Number,
        Sha256 = version.Sha256,
        Format = version.Format switch
        {
            Formats.SchematicFormat.Litematica => SchematicFormat.Litematica,
            Formats.SchematicFormat.Sponge => SchematicFormat.Sponge,
            Formats.SchematicFormat.Structure => SchematicFormat.Structure,
            _ => throw new ArgumentOutOfRangeException(nameof(version), version.Format, null),
        },
        SizeBytes = version.SizeBytes,
        Dimensions = new Dimensions { Width = version.Width, Height = version.Height, Length = version.Length },
        DataVersion = version.DataVersion,
        BlockTotal = version.BlockTotal,
        BlockCounts = version.BlockCounts.Select(count => new BlockCount { Block = count.Block, Count = count.Count }).ToList(),
        SkippedEntities = version.SkippedEntities,
        HasThumbnail = version.HasThumbnail,
        CreatedAt = new DateTimeOffset(version.CreatedAt, TimeSpan.Zero),
    };

    public static SchematicSummary ToSummary(this SchematicDocument schematic) => new()
    {
        Id = schematic.Id,
        Name = schematic.Name,
        Folder = schematic.Folder,
        Tags = schematic.Tags.ToList(),
        UpdatedAt = new DateTimeOffset(schematic.UpdatedAt, TimeSpan.Zero),
        Latest = schematic.Latest.ToContract(),
    };

    public static SchematicDetails ToDetails(this SchematicDocument schematic, IEnumerable<VersionDocument> versions) => new()
    {
        Id = schematic.Id,
        Name = schematic.Name,
        Folder = schematic.Folder,
        Tags = schematic.Tags.ToList(),
        UpdatedAt = new DateTimeOffset(schematic.UpdatedAt, TimeSpan.Zero),
        Versions = versions.OrderBy(version => version.Number).Select(ToContract).ToList(),
    };
}

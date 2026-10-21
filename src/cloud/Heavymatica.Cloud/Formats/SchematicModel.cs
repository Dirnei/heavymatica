using System.Security.Cryptography;
using System.Text;

namespace Heavymatica.Cloud.Formats;

public enum SchematicFormat
{
    Litematica,
    Sponge,
    Structure,
}

public readonly record struct BlockPosition(int X, int Y, int Z);

/// <summary>
/// The canonical model every accepted file is read into: a size, a block state at every
/// position as an index into <see cref="Palette"/> (x fastest, then z, then y), block entities by position, and
/// the data version when the file records one.
/// </summary>
public sealed class SchematicModel
{
    public SchematicModel(
        SchematicFormat format,
        int width,
        int height,
        int length,
        IReadOnlyList<string> palette,
        int[] blocks,
        IReadOnlyDictionary<BlockPosition, NbtCompound> blockEntities,
        int? dataVersion,
        int skippedEntities)
    {
        ArgumentNullException.ThrowIfNull(blocks);
        if (blocks.Length != (long)width * height * length)
        {
            throw new ArgumentException("The block array does not match the size.", nameof(blocks));
        }

        Format = format;
        Width = width;
        Height = height;
        Length = length;
        Palette = palette;
        Blocks = blocks;
        BlockEntities = blockEntities;
        DataVersion = dataVersion;
        SkippedEntities = skippedEntities;
    }

    public SchematicFormat Format { get; }

    public int Width { get; }

    public int Height { get; }

    public int Length { get; }

    /// <summary>Canonical block-state strings (see <see cref="BlockStates.Canonical"/>).</summary>
    public IReadOnlyList<string> Palette { get; }

    public int[] Blocks { get; }

    public IReadOnlyDictionary<BlockPosition, NbtCompound> BlockEntities { get; }

    public int? DataVersion { get; }

    public int SkippedEntities { get; }

    public static int Index(int x, int y, int z, int width, int length) => x + (z * width) + (y * width * length);

    public string StateAt(int x, int y, int z) => Palette[Blocks[Index(x, y, z, Width, Length)]];

    /// <summary>Counts per block ID, properties ignored, air and structure void excluded; most common first.</summary>
    public IReadOnlyList<KeyValuePair<string, long>> BlockCounts()
    {
        var perPalette = new long[Palette.Count];
        foreach (var index in Blocks)
        {
            perPalette[index]++;
        }

        var counts = new Dictionary<string, long>(StringComparer.Ordinal);
        for (var i = 0; i < perPalette.Length; i++)
        {
            var id = BlockStates.Id(Palette[i]);
            if (perPalette[i] > 0 && !BlockStates.IsAirLike(id))
            {
                counts[id] = counts.GetValueOrDefault(id) + perPalette[i];
            }
        }

        return counts.OrderByDescending(pair => pair.Value).ThenBy(pair => pair.Key, StringComparer.Ordinal).ToList();
    }

    /// <summary>SHA-256 over every position's canonical state followed by a newline, in canonical order.</summary>
    public string PaletteDigest()
    {
        var encoded = Palette.Select(state => Encoding.UTF8.GetBytes(state + "\n")).ToArray();
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        foreach (var index in Blocks)
        {
            hash.AppendData(encoded[index]);
        }

        return Convert.ToHexStringLower(hash.GetHashAndReset());
    }
}

/// <summary>Canonical block-state strings: <c>id[key=value,...]</c> with keys sorted ordinally.</summary>
public static class BlockStates
{
    public const string Air = "minecraft:air";
    public const string StructureVoid = "minecraft:structure_void";

    private static readonly HashSet<string> AirLike = new(StringComparer.Ordinal)
    {
        Air, "minecraft:cave_air", "minecraft:void_air", StructureVoid,
    };

    public static bool IsAirLike(string blockId) => AirLike.Contains(blockId);

    public static string Id(string state)
    {
        var bracket = state.IndexOf('[', StringComparison.Ordinal);
        return bracket < 0 ? state : state[..bracket];
    }

    public static string Canonical(string id, IEnumerable<KeyValuePair<string, string>> properties)
    {
        var namespaced = id.Contains(':', StringComparison.Ordinal) ? id : "minecraft:" + id;
        var sorted = properties.OrderBy(pair => pair.Key, StringComparer.Ordinal).ToList();
        if (sorted.Count == 0)
        {
            return namespaced;
        }

        var builder = new StringBuilder(namespaced).Append('[');
        for (var i = 0; i < sorted.Count; i++)
        {
            if (i > 0)
            {
                builder.Append(',');
            }

            builder.Append(sorted[i].Key).Append('=').Append(sorted[i].Value);
        }

        return builder.Append(']').ToString();
    }

    /// <summary>Parses a state string such as Sponge palette keys use and returns its canonical form.</summary>
    public static string Canonical(string state)
    {
        var bracket = state.IndexOf('[', StringComparison.Ordinal);
        if (bracket < 0)
        {
            return Canonical(state, []);
        }

        if (!state.EndsWith(']'))
        {
            throw new SchematicRejectedException(RejectionCode.MalformedNbt, $"Invalid block state '{state}'.");
        }

        var properties = state[(bracket + 1)..^1]
            .Split(',', StringSplitOptions.RemoveEmptyEntries)
            .Select(part =>
            {
                var equals = part.IndexOf('=', StringComparison.Ordinal);
                return equals <= 0
                    ? throw new SchematicRejectedException(RejectionCode.MalformedNbt, $"Invalid block state '{state}'.")
                    : new KeyValuePair<string, string>(part[..equals], part[(equals + 1)..]);
            });
        return Canonical(state[..bracket], properties);
    }

    /// <summary>Reads the <c>{Name, Properties}</c> compound used by structure and Litematica palettes.</summary>
    public static string FromCompound(NbtCompound entry)
    {
        var name = entry.String("Name")
            ?? throw new SchematicRejectedException(RejectionCode.MalformedNbt, "A palette entry has no Name.");
        var properties = entry.Compound("Properties")?.Entries
            .Select(pair => new KeyValuePair<string, string>(pair.Key, (pair.Value as NbtString)?.Value
                ?? throw new SchematicRejectedException(RejectionCode.MalformedNbt, "A block property is not a string.")))
            ?? [];
        return Canonical(name, properties);
    }
}

using System.Globalization;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Heavymatica.Cloud.Features.Schematics.Thumbnails;

internal enum BlockShape
{
    /// <summary>A full block.</summary>
    Cube,

    /// <summary>A box lower than a block, drawn with its height.</summary>
    Slab,

    /// <summary>A plant, torch or other thin model, drawn as a narrow column.</summary>
    Cross,

    /// <summary>Not drawn.</summary>
    None,
}

/// <param name="Top">RGB of the upward faces.</param>
/// <param name="Side">RGB of the side faces.</param>
/// <param name="Alpha">0 to 1.</param>
/// <param name="Height">0 to 1, for slabs; 1 otherwise.</param>
internal readonly record struct BlockColor(int Top, int Side, float Alpha, BlockShape Shape, float Height)
{
    public bool IsOpaqueCube => Shape == BlockShape.Cube && Alpha >= 1f;
}

/// <summary>Average colours, alpha and shape per block ID, made from the game's textures (see the mod's dev steps).</summary>
internal sealed class BlockColors
{
    public const string ResourceName = "block-colors.json";

    /// <summary>For blocks the table does not know, such as other mods' blocks.</summary>
    public static readonly BlockColor Unknown = new(0x9A9A9A, 0x9A9A9A, 1f, BlockShape.Cube, 1f);

    private static readonly Lazy<BlockColors> EmbeddedTable = new(() =>
    {
        using var stream = typeof(BlockColors).Assembly.GetManifestResourceStream(ResourceName)
            ?? throw new InvalidOperationException("The block colour table is not embedded.");
        return Parse(stream);
    });

    private readonly Dictionary<string, BlockColor> colors;

    private BlockColors(Dictionary<string, BlockColor> colors) => this.colors = colors;

    public static BlockColors Embedded => EmbeddedTable.Value;

    public int Count => colors.Count;

    public static BlockColors Parse(Stream json)
    {
        var entries = JsonSerializer.Deserialize<Dictionary<string, Entry>>(json, JsonOptions)
            ?? throw new InvalidDataException("The block colour table is empty.");
        return new BlockColors(entries.ToDictionary(pair => pair.Key, pair => pair.Value.ToColor(pair.Key), StringComparer.Ordinal));
    }

    /// <param name="blockId">A namespaced block ID without properties.</param>
    public BlockColor Of(string blockId) => colors.GetValueOrDefault(blockId, Unknown);

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.CamelCase) },
    };

    private sealed record Entry(string Top, string Side, float Alpha = 1f, BlockShape Shape = BlockShape.Cube, float Height = 1f)
    {
        public BlockColor ToColor(string id) => new(Rgb(Top, id), Rgb(Side, id), Math.Clamp(Alpha, 0f, 1f), Shape, Math.Clamp(Height, 0f, 1f));

        private static int Rgb(string hex, string id) =>
            hex is ['#', .. var digits] && digits.Length == 6 && int.TryParse(digits, NumberStyles.HexNumber, CultureInfo.InvariantCulture, out var rgb)
                ? rgb
                : throw new InvalidDataException($"The colour '{hex}' of {id} is not #rrggbb.");
    }
}

namespace Heavymatica.Cloud.Formats;

/// <summary>
/// Sponge schematics, versions 2 and 3. Both store a palette of state strings and varint-encoded block data in
/// x-z-y order; version 3 nests everything under <c>Schematic</c> and the blocks under <c>Blocks</c>.
/// </summary>
internal static class SpongeFormat
{
    public static SchematicModel Read(NbtCompound root)
    {
        if (root.Compound("Schematic") is { } v3 && v3.Int("Version") == 3)
        {
            var blocks = v3.Compound("Blocks");
            return Read(v3, blocks?.Compound("Palette"), blocks is null ? null : blocks["Data"] ?? Missing, blocks?.List("BlockEntities"), nestedData: true);
        }

        return Read(root, root.Compound("Palette"), root["BlockData"], root.List("BlockEntities"), nestedData: false);
    }

    /// <summary>Marks a Blocks compound without Data, which cannot match any volume.</summary>
    private static readonly NbtByteArray Missing = new([]);

    private static SchematicModel Read(NbtCompound schematic, NbtCompound? paletteTag, NbtTag? data, NbtList? blockEntityTags, bool nestedData)
    {
        var width = Dimension(schematic, "Width");
        var height = Dimension(schematic, "Height");
        var length = Dimension(schematic, "Length");
        SchematicLimits.CheckDimensions(width, height, length);
        var volume = width * height * length;

        // A version 3 schematic without Blocks holds no blocks at all.
        if (data is null)
        {
            return new SchematicModel(SchematicFormat.Sponge, width, height, length, [BlockStates.Air], new int[volume],
                new Dictionary<BlockPosition, NbtCompound>(), schematic.Int("DataVersion"), schematic.List("Entities")?.Items.Count ?? 0);
        }

        if (paletteTag is null)
        {
            throw SchematicReader.Malformed("The schematic has no palette.");
        }

        var paletteSize = paletteTag.Entries.Select(pair => (pair.Value as NbtNumber)?.Value ?? -1).DefaultIfEmpty(-1).Max() + 1;
        var palette = new string?[paletteSize];
        foreach (var (state, index) in paletteTag.Entries)
        {
            if (index is not NbtNumber number || number.Value < 0)
            {
                throw SchematicReader.Malformed($"Palette entry '{state}' has no valid index.");
            }

            palette[number.Value] = BlockStates.Canonical(state);
        }

        var bytes = (data as NbtByteArray)?.Value ?? throw SchematicReader.Malformed("Block data is not a byte array.");
        var blocks = DecodeVarints(bytes, volume, palette);

        var blockEntities = new Dictionary<BlockPosition, NbtCompound>();
        foreach (var item in blockEntityTags?.Items ?? [])
        {
            var entity = item as NbtCompound ?? throw SchematicReader.Malformed("A block entity is not a compound.");
            var pos = SchematicReader.IntTriple(entity["Pos"], "A block entity position");
            var id = entity.String("Id") ?? entity.String("id") ?? string.Empty;
            var fields = nestedData ? entity.Compound("Data")?.Entries ?? [] : entity.Entries;
            blockEntities[new BlockPosition(pos[0], pos[1], pos[2])] = SchematicReader.BlockEntity(id, fields);
        }

        // Palette slots no block refers to may be empty; give them a placeholder so the palette stays dense.
        var dense = palette.Select(state => state ?? BlockStates.Air).ToList();
        return new SchematicModel(SchematicFormat.Sponge, width, height, length, dense, blocks, blockEntities,
            schematic.Int("DataVersion"), schematic.List("Entities")?.Items.Count ?? 0);
    }

    private static int[] DecodeVarints(byte[] bytes, int volume, string?[] palette)
    {
        var blocks = new int[volume];
        var count = 0;
        var position = 0;
        while (position < bytes.Length)
        {
            var value = 0;
            var shift = 0;
            byte current;
            do
            {
                if (position >= bytes.Length || shift > 28)
                {
                    throw SchematicReader.Malformed("Block data holds an invalid varint.");
                }

                current = bytes[position++];
                value |= (current & 0x7F) << shift;
                shift += 7;
            }
            while ((current & 0x80) != 0);

            if (count >= volume)
            {
                throw new SchematicRejectedException(RejectionCode.SizeMismatch, $"Block data holds more than {volume} entries.");
            }

            if (value < 0 || value >= palette.Length || palette[value] is null)
            {
                throw new SchematicRejectedException(RejectionCode.PaletteIndexOutOfRange,
                    $"Block data refers to palette index {value}, the palette has {palette.Count(state => state is not null)} entries.");
            }

            blocks[count++] = value;
        }

        if (count != volume)
        {
            throw new SchematicRejectedException(RejectionCode.SizeMismatch, $"Block data holds {count} entries for a volume of {volume}.");
        }

        return blocks;
    }

    private static int Dimension(NbtCompound schematic, string name) =>
        schematic[name] is NbtNumber number
            ? (int)(number.Value & 0xFFFF)
            : throw SchematicReader.Malformed($"The schematic has no {name}.");
}

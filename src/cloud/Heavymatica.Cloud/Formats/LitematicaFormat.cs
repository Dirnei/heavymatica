namespace Heavymatica.Cloud.Formats;

/// <summary>
/// Litematica files. Each region has a corner <c>Position</c> and a signed <c>Size</c>; its blocks are stored
/// from the region's minimum corner, bit-packed into a long array where entries may span two longs. The model
/// covers the bounding box of all regions; regions are applied in file order, so later regions win. Positions no
/// region covers are structure voids.
/// </summary>
internal static class LitematicaFormat
{
    private sealed record Region(string Name, int[] Min, int[] Size, NbtCompound Tag);

    private const int Uncovered = -1;

    public static SchematicModel Read(NbtCompound root)
    {
        var regionsTag = root.Compound("Regions")!;
        var regions = regionsTag.Entries.Select(pair => ToRegion(pair.Key, pair.Value)).ToList();
        if (regions.Count == 0)
        {
            throw SchematicReader.Malformed("The file has no regions.");
        }

        var origin = new int[3];
        var size = new int[3];
        for (var axis = 0; axis < 3; axis++)
        {
            origin[axis] = regions.Min(region => region.Min[axis]);
            size[axis] = (int)Math.Min(int.MaxValue, regions.Max(region => (long)region.Min[axis] + region.Size[axis]) - origin[axis]);
        }

        SchematicLimits.CheckDimensions(size[0], size[1], size[2]);
        var (width, height, length) = (size[0], size[1], size[2]);

        var palette = new List<string> { BlockStates.Air };
        var paletteIndex = new Dictionary<string, int>(StringComparer.Ordinal) { [BlockStates.Air] = 0 };
        var blocks = new int[width * height * length];
        Array.Fill(blocks, Uncovered);
        var blockEntities = new Dictionary<BlockPosition, NbtCompound>();
        var skippedEntities = 0;

        foreach (var region in regions)
        {
            var regionPalette = (region.Tag.List("BlockStatePalette") ?? throw SchematicReader.Malformed($"Region {region.Name} has no palette."))
                .Items
                .Select(entry => BlockStates.FromCompound(entry as NbtCompound ?? throw SchematicReader.Malformed("A palette entry is not a compound.")))
                .Select(state => paletteIndex.TryGetValue(state, out var existing) ? existing : Add(state))
                .ToArray();
            var states = (region.Tag["BlockStates"] as NbtLongArray)?.Value ?? throw SchematicReader.Malformed($"Region {region.Name} has no block states.");
            var (sx, sy, sz) = (region.Size[0], region.Size[1], region.Size[2]);
            var bits = Math.Max(2, 32 - int.LeadingZeroCount(Math.Max(regionPalette.Length, 1) - 1));
            var volume = (long)sx * sy * sz;
            var expectedLongs = (volume * bits + 63) / 64;
            if (states.LongLength != expectedLongs)
            {
                throw new SchematicRejectedException(RejectionCode.SizeMismatch,
                    $"Region {region.Name} stores {states.Length} longs, {expectedLongs} are needed for {volume} blocks.");
            }

            var baseX = region.Min[0] - origin[0];
            var baseY = region.Min[1] - origin[1];
            var baseZ = region.Min[2] - origin[2];
            var mask = (1L << bits) - 1;
            long index = 0;
            for (var y = 0; y < sy; y++)
            {
                for (var z = 0; z < sz; z++)
                {
                    for (var x = 0; x < sx; x++, index++)
                    {
                        var value = Unpack(states, index, bits, mask);
                        if (value >= regionPalette.Length)
                        {
                            throw new SchematicRejectedException(RejectionCode.PaletteIndexOutOfRange,
                                $"Region {region.Name} refers to palette index {value}, the palette has {regionPalette.Length} entries.");
                        }

                        var target = new BlockPosition(baseX + x, baseY + y, baseZ + z);
                        blocks[SchematicModel.Index(target.X, target.Y, target.Z, width, length)] = regionPalette[value];
                        blockEntities.Remove(target);
                    }
                }
            }

            foreach (var item in region.Tag.List("TileEntities")?.Items ?? [])
            {
                var entity = item as NbtCompound ?? throw SchematicReader.Malformed("A tile entity is not a compound.");
                var (x, y, z) = (entity.Int("x") ?? 0, entity.Int("y") ?? 0, entity.Int("z") ?? 0);
                blockEntities[new BlockPosition(baseX + x, baseY + y, baseZ + z)] =
                    SchematicReader.BlockEntity(entity.String("id") ?? string.Empty, entity.Entries);
            }

            skippedEntities += region.Tag.List("Entities")?.Items.Count ?? 0;
        }

        var voidIndex = Uncovered;
        for (var i = 0; i < blocks.Length; i++)
        {
            if (blocks[i] == Uncovered)
            {
                if (voidIndex == Uncovered)
                {
                    voidIndex = paletteIndex.TryGetValue(BlockStates.StructureVoid, out var existing) ? existing : Add(BlockStates.StructureVoid);
                }

                blocks[i] = voidIndex;
            }
        }

        return new SchematicModel(SchematicFormat.Litematica, width, height, length, palette, blocks, blockEntities,
            root.Int("MinecraftDataVersion"), skippedEntities);

        int Add(string state)
        {
            paletteIndex[state] = palette.Count;
            palette.Add(state);
            return palette.Count - 1;
        }
    }

    /// <summary>LitematicaBitArray: the entry starts at bit index*bits and may continue into the next long.</summary>
    private static long Unpack(long[] states, long index, int bits, long mask)
    {
        var start = index * bits;
        var startLong = (int)(start >> 6);
        var endLong = (int)((((index + 1) * bits) - 1) >> 6);
        var offset = (int)(start & 63);
        if (startLong == endLong)
        {
            return (long)((ulong)states[startLong] >> offset) & mask;
        }

        return ((long)((ulong)states[startLong] >> offset) | (states[endLong] << (64 - offset))) & mask;
    }

    private static Region ToRegion(string name, NbtTag tag)
    {
        var region = tag as NbtCompound ?? throw SchematicReader.Malformed($"Region {name} is not a compound.");
        var position = Vector(region.Compound("Position"), $"Region {name} position");
        var signed = Vector(region.Compound("Size"), $"Region {name} size");
        if (signed.Any(component => component == 0))
        {
            throw SchematicReader.Malformed($"Region {name} has a zero size.");
        }

        // A negative size extends the region from its position towards smaller coordinates.
        var min = new int[3];
        var size = new int[3];
        for (var axis = 0; axis < 3; axis++)
        {
            size[axis] = Math.Abs(signed[axis]);
            min[axis] = signed[axis] < 0 ? position[axis] + signed[axis] + 1 : position[axis];
        }

        if (size.Any(component => component > SchematicLimits.MaxDimension))
        {
            throw new SchematicRejectedException(RejectionCode.DimensionsTooLarge, $"Region {name} exceeds {SchematicLimits.MaxDimension} blocks.");
        }

        return new Region(name, min, size, region);
    }

    private static int[] Vector(NbtCompound? tag, string what) =>
        tag is not null && tag.Int("x") is { } x && tag.Int("y") is { } y && tag.Int("z") is { } z
            ? [x, y, z]
            : throw SchematicReader.Malformed($"{what} is missing.");
}

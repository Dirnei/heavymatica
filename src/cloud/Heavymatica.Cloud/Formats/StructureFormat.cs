namespace Heavymatica.Cloud.Formats;

/// <summary>
/// Vanilla structure-block files: <c>size</c>, <c>palette</c> (or several <c>palettes</c>, of which the first is
/// used), <c>blocks</c> with a palette index and position each, and <c>entities</c>. Positions missing from
/// <c>blocks</c> are structure voids, because structure blocks do not save them.
/// </summary>
internal static class StructureFormat
{
    public static SchematicModel Read(NbtCompound root)
    {
        var size = SchematicReader.IntTriple(root["size"], "size");
        SchematicLimits.CheckDimensions(size[0], size[1], size[2]);
        var (width, height, length) = (size[0], size[1], size[2]);

        var paletteTag = root.List("palette")
            ?? (root.List("palettes") is { Items.Count: > 0 } palettes ? palettes.Items[0] as NbtList : null)
            ?? throw SchematicReader.Malformed("The structure has no palette.");
        var palette = paletteTag.Items
            .Select(entry => BlockStates.FromCompound(entry as NbtCompound ?? throw SchematicReader.Malformed("A palette entry is not a compound.")))
            .ToList();

        var voidIndex = palette.IndexOf(BlockStates.StructureVoid);
        if (voidIndex < 0)
        {
            voidIndex = palette.Count;
            palette.Add(BlockStates.StructureVoid);
        }

        var blocks = new int[width * height * length];
        Array.Fill(blocks, voidIndex);
        var blockEntities = new Dictionary<BlockPosition, NbtCompound>();
        foreach (var item in root.List("blocks")!.Items)
        {
            var block = item as NbtCompound ?? throw SchematicReader.Malformed("A block entry is not a compound.");
            var state = block.Int("state") ?? throw SchematicReader.Malformed("A block entry has no state.");
            if (state < 0 || state >= paletteTag.Items.Count)
            {
                throw new SchematicRejectedException(RejectionCode.PaletteIndexOutOfRange,
                    $"Block state {state} is outside the palette of {paletteTag.Items.Count} entries.");
            }

            var pos = SchematicReader.IntTriple(block["pos"], "A block position");
            if (pos[0] < 0 || pos[1] < 0 || pos[2] < 0 || pos[0] >= width || pos[1] >= height || pos[2] >= length)
            {
                throw new SchematicRejectedException(RejectionCode.SizeMismatch, $"Block position {string.Join(',', pos)} is outside the size.");
            }

            blocks[SchematicModel.Index(pos[0], pos[1], pos[2], width, length)] = state;
            if (block.Compound("nbt") is { } nbt)
            {
                blockEntities[new BlockPosition(pos[0], pos[1], pos[2])] =
                    SchematicReader.BlockEntity(nbt.String("id") ?? string.Empty, nbt.Entries);
            }
        }

        return new SchematicModel(SchematicFormat.Structure, width, height, length, palette, blocks, blockEntities,
            root.Int("DataVersion"), root.List("entities")?.Items.Count ?? 0);
    }
}

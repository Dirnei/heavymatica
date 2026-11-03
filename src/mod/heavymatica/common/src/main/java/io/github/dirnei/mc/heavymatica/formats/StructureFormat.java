package io.github.dirnei.mc.heavymatica.formats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla structure-block files: {@code size}, {@code palette} (or several {@code palettes}, of which the first is
 * used), {@code blocks} with a palette index and position each, and {@code entities}. Positions missing from
 * {@code blocks} are structure voids, because structure blocks do not save them.
 */
final class StructureFormat {

    private StructureFormat() {
    }

    static SchematicModel read(Nbt.Compound root) {
        int[] size = Nbt.intTriple(root.get("size"), "size");
        SchematicLimits.checkDimensions(size[0], size[1], size[2]);
        int width = size[0], height = size[1], length = size[2];

        Nbt.ListTag paletteTag = root.list("palette");
        if (paletteTag == null) {
            Nbt.ListTag palettes = root.list("palettes");
            if (palettes != null && !palettes.items().isEmpty() && palettes.items().getFirst() instanceof Nbt.ListTag first) {
                paletteTag = first;
            }
        }
        if (paletteTag == null) {
            throw SchematicRejectedException.malformed("The structure has no palette.");
        }
        List<String> palette = new ArrayList<>(BlockStates.fromPalette(paletteTag));
        int paletteSize = palette.size();
        int voidIndex = palette.indexOf(BlockStates.STRUCTURE_VOID);
        if (voidIndex < 0) {
            voidIndex = palette.size();
            palette.add(BlockStates.STRUCTURE_VOID);
        }

        int[] blocks = new int[width * height * length];
        Arrays.fill(blocks, voidIndex);
        Map<SchematicModel.Pos, Nbt.Compound> blockEntities = new HashMap<>();
        for (Nbt.Tag item : root.list("blocks").items()) {
            if (!(item instanceof Nbt.Compound block)) {
                throw SchematicRejectedException.malformed("A block entry is not a compound.");
            }
            Integer state = block.integer("state");
            if (state == null) {
                throw SchematicRejectedException.malformed("A block entry has no state.");
            }
            if (state < 0 || state >= paletteSize) {
                throw new SchematicRejectedException(RejectionCode.PALETTE_INDEX_OUT_OF_RANGE,
                        "Block state " + state + " is outside the palette of " + paletteSize + " entries.");
            }
            int[] pos = Nbt.intTriple(block.get("pos"), "A block position");
            if (pos[0] < 0 || pos[1] < 0 || pos[2] < 0 || pos[0] >= width || pos[1] >= height || pos[2] >= length) {
                throw new SchematicRejectedException(RejectionCode.SIZE_MISMATCH, "Block position " + Arrays.toString(pos) + " is outside the size.");
            }
            blocks[SchematicModel.index(pos[0], pos[1], pos[2], width, length)] = state;
            Nbt.Compound nbt = block.compound("nbt");
            if (nbt != null) {
                String id = nbt.string("id");
                blockEntities.put(new SchematicModel.Pos(pos[0], pos[1], pos[2]), SchematicReader.blockEntity(id == null ? "" : id, nbt.entries()));
            }
        }

        Nbt.ListTag entities = root.list("entities");
        return new SchematicModel(SchematicFormat.STRUCTURE, width, height, length, palette, blocks, blockEntities,
                root.integer("DataVersion"), entities == null ? 0 : entities.items().size());
    }
}

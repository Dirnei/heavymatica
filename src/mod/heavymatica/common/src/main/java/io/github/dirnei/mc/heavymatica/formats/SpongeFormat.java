package io.github.dirnei.mc.heavymatica.formats;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Sponge schematics, versions 2 and 3. Both store a palette of state strings and varint-encoded block data in
 * x-z-y order; version 3 nests everything under {@code Schematic} and the blocks under {@code Blocks}.
 */
final class SpongeFormat {

    /** Marks a Blocks compound without Data, which cannot match any volume. */
    private static final Nbt.ByteArray MISSING = new Nbt.ByteArray(new byte[0]);

    private SpongeFormat() {
    }

    static SchematicModel read(Nbt.Compound root) {
        Nbt.Compound v3 = root.compound("Schematic");
        if (v3 != null && Integer.valueOf(3).equals(v3.integer("Version"))) {
            Nbt.Compound blocks = v3.compound("Blocks");
            Nbt.Tag data = blocks == null ? null : (blocks.get("Data") == null ? MISSING : blocks.get("Data"));
            return read(v3, blocks == null ? null : blocks.compound("Palette"), data, blocks == null ? null : blocks.list("BlockEntities"), true);
        }
        return read(root, root.compound("Palette"), root.get("BlockData"), root.list("BlockEntities"), false);
    }

    private static SchematicModel read(Nbt.Compound schematic, @Nullable Nbt.Compound paletteTag, @Nullable Nbt.Tag data,
                                       @Nullable Nbt.ListTag blockEntityTags, boolean nestedData) {
        int width = dimension(schematic, "Width");
        int height = dimension(schematic, "Height");
        int length = dimension(schematic, "Length");
        SchematicLimits.checkDimensions(width, height, length);
        int volume = width * height * length;
        Nbt.ListTag entities = schematic.list("Entities");
        int skipped = entities == null ? 0 : entities.items().size();

        // A version 3 schematic without Blocks holds no blocks at all.
        if (data == null) {
            return new SchematicModel(SchematicFormat.SPONGE, width, height, length, List.of(BlockStates.AIR), new int[volume], Map.of(),
                    schematic.integer("DataVersion"), skipped);
        }
        if (paletteTag == null) {
            throw SchematicRejectedException.malformed("The schematic has no palette.");
        }

        long paletteSize = paletteTag.entries().values().stream()
                .mapToLong(tag -> tag instanceof Nbt.Number number ? number.value() : -1).max().orElse(-1) + 1;
        String[] palette = new String[Math.toIntExact(Math.min(paletteSize, Integer.MAX_VALUE))];
        for (Map.Entry<String, Nbt.Tag> entry : paletteTag.entries().entrySet()) {
            if (!(entry.getValue() instanceof Nbt.Number number) || number.value() < 0) {
                throw SchematicRejectedException.malformed("Palette entry '" + entry.getKey() + "' has no valid index.");
            }
            palette[(int) number.value()] = BlockStates.canonical(entry.getKey());
        }

        if (!(data instanceof Nbt.ByteArray bytes)) {
            throw SchematicRejectedException.malformed("Block data is not a byte array.");
        }
        int[] blocks = decodeVarints(bytes.value(), volume, palette);

        Map<SchematicModel.Pos, Nbt.Compound> blockEntities = new HashMap<>();
        for (Nbt.Tag item : blockEntityTags == null ? List.<Nbt.Tag>of() : blockEntityTags.items()) {
            if (!(item instanceof Nbt.Compound entity)) {
                throw SchematicRejectedException.malformed("A block entity is not a compound.");
            }
            int[] pos = Nbt.intTriple(entity.get("Pos"), "A block entity position");
            String id = entity.string("Id") != null ? entity.string("Id") : entity.string("id");
            Map<String, Nbt.Tag> fields = nestedData
                    ? (entity.compound("Data") == null ? Map.of() : entity.compound("Data").entries())
                    : entity.entries();
            blockEntities.put(new SchematicModel.Pos(pos[0], pos[1], pos[2]), SchematicReader.blockEntity(id == null ? "" : id, fields));
        }

        // Palette slots no block refers to may be empty; give them a placeholder so the palette stays dense.
        List<String> dense = Arrays.stream(palette).map(state -> state == null ? BlockStates.AIR : state).toList();
        return new SchematicModel(SchematicFormat.SPONGE, width, height, length, dense, blocks, blockEntities,
                schematic.integer("DataVersion"), skipped);
    }

    private static int[] decodeVarints(byte[] bytes, int volume, String[] palette) {
        int[] blocks = new int[volume];
        int count = 0;
        int position = 0;
        while (position < bytes.length) {
            int value = 0;
            int shift = 0;
            byte current;
            do {
                if (position >= bytes.length || shift > 28) {
                    throw SchematicRejectedException.malformed("Block data holds an invalid varint.");
                }
                current = bytes[position++];
                value |= (current & 0x7F) << shift;
                shift += 7;
            } while ((current & 0x80) != 0);

            if (count >= volume) {
                throw new SchematicRejectedException(RejectionCode.SIZE_MISMATCH, "Block data holds more than " + volume + " entries.");
            }
            if (value < 0 || value >= palette.length || palette[value] == null) {
                long entries = Arrays.stream(palette).filter(state -> state != null).count();
                throw new SchematicRejectedException(RejectionCode.PALETTE_INDEX_OUT_OF_RANGE,
                        "Block data refers to palette index " + value + ", the palette has " + entries + " entries.");
            }
            blocks[count++] = value;
        }
        if (count != volume) {
            throw new SchematicRejectedException(RejectionCode.SIZE_MISMATCH, "Block data holds " + count + " entries for a volume of " + volume + ".");
        }
        return blocks;
    }

    private static int dimension(Nbt.Compound schematic, String name) {
        if (!(schematic.get(name) instanceof Nbt.Number number)) {
            throw SchematicRejectedException.malformed("The schematic has no " + name + ".");
        }
        return (int) (number.value() & 0xFFFF);
    }
}

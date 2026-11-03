package io.github.dirnei.mc.heavymatica.formats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Litematica files. Each region has a corner {@code Position} and a signed {@code Size}; its blocks are stored from
 * the region's minimum corner, bit-packed into a long array where entries may span two longs. The model covers the
 * bounding box of all regions; regions are applied in file order, so later regions win. Positions no region covers
 * are structure voids.
 */
final class LitematicaFormat {

    private record Region(String name, int[] min, int[] size, Nbt.Compound tag) {
    }

    private static final int UNCOVERED = -1;

    private LitematicaFormat() {
    }

    static SchematicModel read(Nbt.Compound root) {
        List<Region> regions = new ArrayList<>();
        root.compound("Regions").entries().forEach((name, tag) -> regions.add(region(name, tag)));
        if (regions.isEmpty()) {
            throw SchematicRejectedException.malformed("The file has no regions.");
        }

        int[] origin = new int[3];
        int[] size = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            final int a = axis;
            origin[axis] = regions.stream().mapToInt(region -> region.min()[a]).min().orElseThrow();
            long max = regions.stream().mapToLong(region -> (long) region.min()[a] + region.size()[a]).max().orElseThrow();
            size[axis] = (int) Math.min(Integer.MAX_VALUE, max - origin[axis]);
        }
        SchematicLimits.checkDimensions(size[0], size[1], size[2]);
        int width = size[0], height = size[1], length = size[2];

        List<String> palette = new ArrayList<>(List.of(BlockStates.AIR));
        Map<String, Integer> paletteIndex = new HashMap<>(Map.of(BlockStates.AIR, 0));
        int[] blocks = new int[width * height * length];
        Arrays.fill(blocks, UNCOVERED);
        Map<SchematicModel.Pos, Nbt.Compound> blockEntities = new HashMap<>();
        int skippedEntities = 0;

        for (Region region : regions) {
            Nbt.ListTag paletteTag = region.tag().list("BlockStatePalette");
            if (paletteTag == null) {
                throw SchematicRejectedException.malformed("Region " + region.name() + " has no palette.");
            }
            int[] regionPalette = BlockStates.fromPalette(paletteTag).stream()
                    .mapToInt(state -> paletteIndex.computeIfAbsent(state, added -> {
                        palette.add(added);
                        return palette.size() - 1;
                    }))
                    .toArray();
            if (!(region.tag().get("BlockStates") instanceof Nbt.LongArray statesTag)) {
                throw SchematicRejectedException.malformed("Region " + region.name() + " has no block states.");
            }
            long[] states = statesTag.value();
            int sx = region.size()[0], sy = region.size()[1], sz = region.size()[2];
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(regionPalette.length, 1) - 1));
            long volume = (long) sx * sy * sz;
            long expectedLongs = (volume * bits + 63) / 64;
            if (states.length != expectedLongs) {
                throw new SchematicRejectedException(RejectionCode.SIZE_MISMATCH,
                        "Region " + region.name() + " stores " + states.length + " longs, " + expectedLongs + " are needed for " + volume + " blocks.");
            }

            int baseX = region.min()[0] - origin[0];
            int baseY = region.min()[1] - origin[1];
            int baseZ = region.min()[2] - origin[2];
            long mask = (1L << bits) - 1;
            long index = 0;
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++, index++) {
                        long value = unpack(states, index, bits, mask);
                        if (value >= regionPalette.length) {
                            throw new SchematicRejectedException(RejectionCode.PALETTE_INDEX_OUT_OF_RANGE,
                                    "Region " + region.name() + " refers to palette index " + value + ", the palette has " + regionPalette.length + " entries.");
                        }
                        SchematicModel.Pos target = new SchematicModel.Pos(baseX + x, baseY + y, baseZ + z);
                        blocks[SchematicModel.index(target.x(), target.y(), target.z(), width, length)] = regionPalette[(int) value];
                        blockEntities.remove(target);
                    }
                }
            }

            Nbt.ListTag tileEntities = region.tag().list("TileEntities");
            for (Nbt.Tag item : tileEntities == null ? List.<Nbt.Tag>of() : tileEntities.items()) {
                if (!(item instanceof Nbt.Compound entity)) {
                    throw SchematicRejectedException.malformed("A tile entity is not a compound.");
                }
                int x = orZero(entity.integer("x")), y = orZero(entity.integer("y")), z = orZero(entity.integer("z"));
                String id = entity.string("id");
                blockEntities.put(new SchematicModel.Pos(baseX + x, baseY + y, baseZ + z), SchematicReader.blockEntity(id == null ? "" : id, entity.entries()));
            }
            Nbt.ListTag entities = region.tag().list("Entities");
            skippedEntities += entities == null ? 0 : entities.items().size();
        }

        int voidIndex = UNCOVERED;
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == UNCOVERED) {
                if (voidIndex == UNCOVERED) {
                    voidIndex = paletteIndex.computeIfAbsent(BlockStates.STRUCTURE_VOID, added -> {
                        palette.add(added);
                        return palette.size() - 1;
                    });
                }
                blocks[i] = voidIndex;
            }
        }

        return new SchematicModel(SchematicFormat.LITEMATICA, width, height, length, palette, blocks, blockEntities,
                root.integer("MinecraftDataVersion"), skippedEntities);
    }

    /** LitematicaBitArray: the entry starts at bit index*bits and may continue into the next long. */
    private static long unpack(long[] states, long index, int bits, long mask) {
        long start = index * bits;
        int startLong = (int) (start >> 6);
        int endLong = (int) (((index + 1) * bits - 1) >> 6);
        int offset = (int) (start & 63);
        if (startLong == endLong) {
            return (states[startLong] >>> offset) & mask;
        }
        return ((states[startLong] >>> offset) | (states[endLong] << (64 - offset))) & mask;
    }

    private static Region region(String name, Nbt.Tag tag) {
        if (!(tag instanceof Nbt.Compound region)) {
            throw SchematicRejectedException.malformed("Region " + name + " is not a compound.");
        }
        int[] position = vector(region.compound("Position"), "Region " + name + " position");
        int[] signed = vector(region.compound("Size"), "Region " + name + " size");
        int[] min = new int[3];
        int[] size = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            if (signed[axis] == 0) {
                throw SchematicRejectedException.malformed("Region " + name + " has a zero size.");
            }
            // A negative size extends the region from its position towards smaller coordinates.
            size[axis] = Math.abs(signed[axis]);
            min[axis] = signed[axis] < 0 ? position[axis] + signed[axis] + 1 : position[axis];
            if (size[axis] > SchematicLimits.MAX_DIMENSION) {
                throw new SchematicRejectedException(RejectionCode.DIMENSIONS_TOO_LARGE, "Region " + name + " exceeds " + SchematicLimits.MAX_DIMENSION + " blocks.");
            }
        }
        return new Region(name, min, size, region);
    }

    private static int[] vector(Nbt.Compound tag, String what) {
        if (tag == null || tag.integer("x") == null || tag.integer("y") == null || tag.integer("z") == null) {
            throw SchematicRejectedException.malformed(what + " is missing.");
        }
        return new int[] {tag.integer("x"), tag.integer("y"), tag.integer("z")};
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}

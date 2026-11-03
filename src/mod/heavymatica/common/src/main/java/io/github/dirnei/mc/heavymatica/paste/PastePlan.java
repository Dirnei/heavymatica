package io.github.dirnei.mc.heavymatica.paste;

import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.formats.Nbt;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * What a paste places, free of the game: the target state of every world position of a placement's box, grouped by
 * 16 x 16 x 16 section in y, then z, then x order, with the schematic's block-entity data moved to its world position.
 * Positions whose block the game does not know and structure voids are left out, so the world stays there.
 *
 * @param <S> the block state type
 */
public final class PastePlan<S> {

    /** The placement's schematic as placed: positions in the rotated box (0-based), states swapped, mirrored and rotated. */
    public interface View<S> {

        int sizeX();

        int sizeY();

        int sizeZ();

        /** The schematic palette index at a position inside the view; {@code scratch} holds two ints. */
        int paletteIndex(int x, int y, int z, int[] scratch);

        /** A palette entry's state string after its swap. */
        String entry(int paletteIndex);

        /** A palette entry as placed; null when the game does not know its block. */
        @Nullable S placed(int paletteIndex);
    }

    /** The planned positions of one section. */
    public static final class Section<S> {

        private final int originX;
        private final int originY;
        private final int originZ;
        private final int[] cells;
        private final Object[] states;
        private final Map<Integer, Nbt.Compound> blockEntities;

        private Section(int originX, int originY, int originZ, int[] cells, Object[] states, Map<Integer, Nbt.Compound> blockEntities) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.cells = cells;
            this.states = states;
            this.blockEntities = blockEntities;
        }

        public int size() {
            return cells.length;
        }

        public int x(int i) {
            return originX + (cells[i] & 15);
        }

        public int y(int i) {
            return originY + (cells[i] >> 8);
        }

        public int z(int i) {
            return originZ + (cells[i] >> 4 & 15);
        }

        @SuppressWarnings("unchecked")
        public S state(int i) {
            return (S) states[i];
        }

        /** The block-entity data for the i-th position, with its world position, or null. */
        public @Nullable Nbt.Compound blockEntity(int i) {
            return blockEntities.get(i);
        }
    }

    private final List<Section<S>> sections;
    private final long total;

    private PastePlan(List<Section<S>> sections, long total) {
        this.sections = sections;
        this.total = total;
    }

    /**
     * @param model   the placement's schematic, for its size before rotation and its block entities
     * @param originX the world x of the placed box's minimum corner, likewise y and z
     */
    public static <S> PastePlan<S> build(View<S> view, SchematicModel model, Turn rotation, Flip mirror, int originX, int originY, int originZ) {
        int paletteSize = model.palette().size();
        Object[] targets = new Object[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            targets[i] = BlockStates.id(view.entry(i)).equals(BlockStates.STRUCTURE_VOID) ? null : view.placed(i);
        }
        Map<Long, Nbt.Compound> blockEntities = new HashMap<>();
        int[] placed = new int[2];
        model.blockEntities().forEach((pos, tag) -> {
            PlacementTransform.toPlaced(rotation, mirror, model.width(), model.length(), pos.x(), pos.z(), placed);
            blockEntities.put(key(placed[0], pos.y(), placed[1]), tag);
        });
        int maxX = originX + view.sizeX() - 1;
        int maxY = originY + view.sizeY() - 1;
        int maxZ = originZ + view.sizeZ() - 1;
        List<Section<S>> sections = new ArrayList<>();
        long total = 0;
        int[] scratch = new int[2];
        int[] cells = new int[4096];
        Object[] states = new Object[4096];
        for (int sy = Math.floorDiv(originY, 16); sy <= Math.floorDiv(maxY, 16); sy++) {
            for (int sz = Math.floorDiv(originZ, 16); sz <= Math.floorDiv(maxZ, 16); sz++) {
                for (int sx = Math.floorDiv(originX, 16); sx <= Math.floorDiv(maxX, 16); sx++) {
                    int count = 0;
                    Map<Integer, Nbt.Compound> sectionEntities = new HashMap<>();
                    for (int y = Math.max(originY, sy * 16); y <= Math.min(maxY, sy * 16 + 15); y++) {
                        for (int z = Math.max(originZ, sz * 16); z <= Math.min(maxZ, sz * 16 + 15); z++) {
                            for (int x = Math.max(originX, sx * 16); x <= Math.min(maxX, sx * 16 + 15); x++) {
                                int lx = x - originX;
                                int ly = y - originY;
                                int lz = z - originZ;
                                Object target = targets[view.paletteIndex(lx, ly, lz, scratch)];
                                if (target == null) {
                                    continue;
                                }
                                Nbt.Compound tag = blockEntities.get(key(lx, ly, lz));
                                if (tag != null) {
                                    sectionEntities.put(count, moved(tag, x, y, z));
                                }
                                cells[count] = (x & 15) | (z & 15) << 4 | (y & 15) << 8;
                                states[count] = target;
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        sections.add(new Section<>(sx * 16, sy * 16, sz * 16, Arrays.copyOf(cells, count), Arrays.copyOf(states, count),
                                sectionEntities));
                        total += count;
                    }
                }
            }
        }
        return new PastePlan<>(List.copyOf(sections), total);
    }

    /** True when the game does not know a palette entry's block: it resolved to air although it names no air. */
    public static boolean unknown(String entry, boolean resolvedToAir) {
        return resolvedToAir && !BlockStates.isAirLike(BlockStates.id(entry));
    }

    public List<Section<S>> sections() {
        return sections;
    }

    /** The number of planned positions. */
    public long total() {
        return total;
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 42) | ((long) y << 21) | z;
    }

    private static Nbt.Compound moved(Nbt.Compound tag, int x, int y, int z) {
        LinkedHashMap<String, Nbt.Tag> entries = new LinkedHashMap<>();
        tag.entries().forEach((name, value) -> {
            if (!name.equals("x") && !name.equals("y") && !name.equals("z")) {
                entries.put(name, value);
            }
        });
        entries.put("x", new Nbt.Number(x, Nbt.INT));
        entries.put("y", new Nbt.Number(y, Nbt.INT));
        entries.put("z", new Nbt.Number(z, Nbt.INT));
        return new Nbt.Compound(entries);
    }
}

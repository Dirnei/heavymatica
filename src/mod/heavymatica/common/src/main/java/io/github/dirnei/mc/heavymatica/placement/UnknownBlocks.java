package io.github.dirnei.mc.heavymatica.placement;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import org.jetbrains.annotations.Nullable;

/**
 * The positions of a schematic whose block the game does not know, per block ID as shown.
 *
 * @param positions block ID to its number of positions, sorted by ID
 */
public record UnknownBlocks(Map<String, Integer> positions) {

    public static final UnknownBlocks NONE = new UnknownBlocks(Map.of());

    /**
     * Counts the positions of a schematic by palette index.
     *
     * @param blocks      the palette index of every position
     * @param paletteSize the number of palette entries
     * @param unknownId   the block ID a palette entry shows when the game does not know it, otherwise null
     */
    public static UnknownBlocks count(int[] blocks, int paletteSize, IntFunction<@Nullable String> unknownId) {
        int[] perEntry = new int[paletteSize];
        for (int entry : blocks) {
            perEntry[entry]++;
        }
        TreeMap<String, Integer> positions = new TreeMap<>();
        for (int entry = 0; entry < paletteSize; entry++) {
            String id = perEntry[entry] == 0 ? null : unknownId.apply(entry);
            if (id != null) {
                positions.merge(id, perEntry[entry], Integer::sum);
            }
        }
        return positions.isEmpty() ? NONE : new UnknownBlocks(Collections.unmodifiableMap(positions));
    }

    public boolean any() {
        return !positions.isEmpty();
    }

    public int types() {
        return positions.size();
    }

    public int total() {
        return positions.values().stream().mapToInt(Integer::intValue).sum();
    }
}

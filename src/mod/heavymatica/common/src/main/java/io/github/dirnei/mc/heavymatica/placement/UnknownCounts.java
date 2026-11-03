package io.github.dirnei.mc.heavymatica.placement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** How many positions of a placement show each block the game does not know, per layer. */
final class UnknownCounts {

    private final int height;
    private final Map<String, int[]> perLayer = new LinkedHashMap<>();

    UnknownCounts(int height) {
        this.height = height;
    }

    void add(int layer, String blockId) {
        perLayer.computeIfAbsent(blockId, id -> new int[height])[layer]++;
    }

    /** Block ID to its number of positions in layers {@code from} to {@code to}, inclusive, sorted by ID; IDs with none left out. */
    Map<String, Integer> sum(int from, int to) {
        TreeMap<String, Integer> sums = new TreeMap<>();
        perLayer.forEach((id, layers) -> {
            int sum = 0;
            for (int layer = from; layer <= to; layer++) {
                sum += layers[layer];
            }
            if (sum > 0) {
                sums.put(id, sum);
            }
        });
        return Collections.unmodifiableMap(sums);
    }
}

package io.github.dirnei.mc.heavymatica.placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds material lists over block ID strings the way the comparison does: items are the block IDs that place them. */
public final class SharedLists {

    private SharedLists() {
    }

    /**
     * @param needed    per planned block ID, how many it still needs
     * @param finishing per planned block ID, how many positions need finishing
     * @param carried   per item, how many the player carries
     */
    public static MaterialList<String, String> of(Map<String, Integer> needed, Map<String, Integer> finishing, Map<String, Integer> carried) {
        List<String> items = new ArrayList<>();
        List<MaterialList.Need<String>> planned = new ArrayList<>();
        TreeMap<String, Integer> sorted = new TreeMap<>(needed);
        finishing.keySet().forEach(block -> sorted.putIfAbsent(block, 0));
        sorted.forEach((block, count) -> {
            BlockEquivalents.Equivalence equivalence = BlockEquivalents.of(block);
            int[] accepts = equivalence.accepted(block).stream().mapToInt(id -> index(items, id)).toArray();
            planned.add(new MaterialList.Need<>(block, index(items, equivalence.placedWith()), accepts, count, finishing.getOrDefault(block, 0),
                    equivalence.finish()));
        });
        carried.keySet().forEach(item -> index(items, item));
        int[] stock = items.stream().mapToInt(item -> carried.getOrDefault(item, 0)).toArray();
        return MaterialList.shared(planned, items, stock, Map.of(), Comparator.naturalOrder(), Comparator.naturalOrder());
    }

    private static int index(List<String> items, String item) {
        int index = items.indexOf(item);
        if (index < 0) {
            items.add(item);
            return items.size() - 1;
        }
        return index;
    }
}

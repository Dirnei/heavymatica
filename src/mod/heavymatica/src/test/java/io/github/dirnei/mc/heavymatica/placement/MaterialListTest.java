package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterialListTest {

    private static MaterialList<String, String> list(Map<String, Integer> needed, Map<String, Integer> had) {
        return list(needed, Map.of(), had);
    }

    private static MaterialList<String, String> list(Map<String, Integer> needed, Map<String, Integer> cantCarry, Map<String, Integer> had) {
        return MaterialList.of(needed, cantCarry, item -> had.getOrDefault(item, 0), Comparator.naturalOrder(), Comparator.naturalOrder());
    }

    private static Map<String, Integer> map(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void shortOnPlanks() {
        MaterialList<String, String> list = list(map("oak_planks", 214), map("oak_planks", 202));
        assertEquals(List.of(new MaterialList.Row<>("oak_planks", 214, 202, 12)), list.rows());
    }

    @Test
    void sortsByShortThenNeededThenItem() {
        MaterialList<String, String> list = list(
                map("glass", 10, "stone", 50, "dirt", 4, "andesite", 4, "torch", 30, "sand", 8),
                map("glass", 8, "stone", 46, "torch", 30, "sand", 6));
        assertEquals(List.of("stone", "andesite", "dirt", "glass", "sand", "torch"), list.rows().stream().map(MaterialList.Row::item).toList());
    }

    @Test
    void shortNeverGoesBelowZero() {
        MaterialList<String, String> list = list(map("oak_planks", 10), map("oak_planks", 74));
        assertEquals(new MaterialList.Row<>("oak_planks", 10, 74, 0), list.rows().getFirst());
    }

    @Test
    void itemsNeededZeroTimesAreLeftOut() {
        MaterialList<String, String> list = list(map("oak_planks", 0, "stone", 1), map());
        assertEquals(List.of("stone"), list.rows().stream().map(MaterialList.Row::item).toList());
    }

    @Test
    void nothingShort() {
        MaterialList<String, String> list = list(map("oak_planks", 10, "stone", 3), map("oak_planks", 10, "stone", 64));
        MaterialList.Shortages<String> shortages = list.shortages(3);
        assertTrue(shortages.none());
        assertEquals(0, shortages.more());
        assertEquals(2, list.rows().size());
    }

    @Test
    void moreThanThreeShort() {
        MaterialList<String, String> list = list(map("oak_planks", 214, "stone_bricks", 4, "glass", 2, "torch", 1, "dirt", 5),
                map("oak_planks", 202, "dirt", 5));
        MaterialList.Shortages<String> shortages = list.shortages(3);
        assertEquals(List.of(new MaterialList.Row<>("oak_planks", 214, 202, 12), new MaterialList.Row<>("stone_bricks", 4, 0, 4),
                new MaterialList.Row<>("glass", 2, 0, 2)), shortages.top());
        assertEquals(1, shortages.more());
    }

    @Test
    void cantCarryIsKeptApart() {
        MaterialList<String, String> list = list(map("oak_planks", 2), map("water", 4, "lava", 1, "fire", 0), map());
        assertEquals(List.of(new MaterialList.CantCarry<>("water", 4), new MaterialList.CantCarry<>("lava", 1)), list.cantCarry());
        assertEquals(List.of("oak_planks"), list.rows().stream().map(MaterialList.Row::item).toList());
        assertEquals(1, list.shortages(3).top().size());
    }
}

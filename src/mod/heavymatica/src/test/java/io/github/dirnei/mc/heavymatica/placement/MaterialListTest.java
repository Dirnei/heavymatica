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

    private static final String DIRT = "minecraft:dirt";
    private static final String GRASS = "minecraft:grass_block";
    private static final String PATH = "minecraft:dirt_path";
    private static final String FARMLAND = "minecraft:farmland";
    private static final String CONCRETE = "minecraft:white_concrete";
    private static final String POWDER = "minecraft:white_concrete_powder";
    private static final String PLANKS = "minecraft:oak_planks";

    private static MaterialList<String, String> shared(Map<String, Integer> needed, Map<String, Integer> carried) {
        return SharedLists.of(needed, Map.of(), carried);
    }

    @Test
    void grassNeedsDirt() {
        MaterialList<String, String> list = shared(map(GRASS, 30, DIRT, 10), map());
        assertEquals(List.of(new MaterialList.Row<>(DIRT, 40, 0, 40)), list.rows());
    }

    @Test
    void pathsAndFarmlandNeedDirt() {
        MaterialList<String, String> list = shared(map(PATH, 6, FARMLAND, 4, GRASS, 2), map());
        assertEquals(List.of(new MaterialList.Row<>(DIRT, 12, 0, 12)), list.rows());
    }

    @Test
    void silkTouchedGrassCountsForGrassAndDirt() {
        MaterialList<String, String> list = shared(map(GRASS, 30, DIRT, 10), map(DIRT, 10, GRASS, 30));
        assertEquals(List.of(new MaterialList.Row<>(DIRT, 40, 40, 0)), list.rows());
    }

    @Test
    void hardenedConcreteCountsForConcrete() {
        MaterialList<String, String> list = shared(map(CONCRETE, 64), map(CONCRETE, 64));
        assertEquals(List.of(new MaterialList.Row<>(POWDER, 64, 64, 0)), list.rows());
    }

    @Test
    void concreteIsNotPowder() {
        MaterialList<String, String> list = shared(map(POWDER, 64), map(CONCRETE, 64));
        assertEquals(List.of(new MaterialList.Row<>(POWDER, 64, 0, 64)), list.rows());
    }

    @Test
    void concreteAndPowderTogether() {
        MaterialList<String, String> list = shared(map(CONCRETE, 64, POWDER, 64), map(POWDER, 96));
        assertEquals(List.of(new MaterialList.Row<>(POWDER, 128, 96, 32)), list.rows());
        assertEquals(new MaterialList.Planned<>(POWDER, 64, 64, 0, 0, 0, null), list.planned(POWDER));
        assertEquals(new MaterialList.Planned<>(POWDER, 64, 32, 32, 0, 0, BlockEquivalents.Finish.WATER), list.planned(CONCRETE));
    }

    @Test
    void pathItemsCoverFarmland() {
        MaterialList<String, String> list = shared(map(FARMLAND, 10), map(PATH, 10));
        assertEquals(List.of(new MaterialList.Row<>(DIRT, 10, 10, 0)), list.rows());
    }

    @Test
    void sparePlanksAreHad() {
        MaterialList<String, String> list = shared(map(PLANKS, 300), map(PLANKS, 512));
        assertEquals(List.of(new MaterialList.Row<>(PLANKS, 300, 512, 0)), list.rows());
        assertEquals(new MaterialList.Planned<>(PLANKS, 300, 300, 0, 212, 0, null), list.planned(PLANKS));
    }

    @Test
    void plannedBlocksKeepTheirFinishing() {
        MaterialList<String, String> list = SharedLists.of(map(PATH, 5, GRASS, 10), map(PATH, 15), map());
        assertEquals(new MaterialList.Planned<>(DIRT, 5, 0, 5, 0, 15, BlockEquivalents.Finish.SHOVEL), list.planned(PATH));
        assertEquals(new MaterialList.Planned<>(DIRT, 10, 0, 10, 0, 0, null), list.planned(GRASS));
        assertEquals(null, list.planned(FARMLAND));
    }
}

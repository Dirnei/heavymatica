package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.dirnei.mc.heavymatica.formats.SchematicModel.BlockCount;
import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import io.github.dirnei.mc.heavymatica.placement.SharedLists;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MaterialRowsTest {

    private static final String CONCRETE = "minecraft:white_concrete";
    private static final String POWDER = "minecraft:white_concrete_powder";
    private static final String GRASS = "minecraft:grass_block";
    private static final String PATH = "minecraft:dirt_path";
    private static final String DIRT = "minecraft:dirt";

    private static final Set<String> KNOWN = Set.of("oak_stairs", "spruce_stairs", "oak_planks", "stone_bricks", "deepslate_bricks", "water", CONCRETE,
            POWDER, GRASS, PATH, DIRT);
    private static final Map<String, Integer> CARRIED = Map.of("oak_planks", 512, "spruce_stairs", 64, "glass", 3);

    private static final List<BlockCount> COUNTS = List.of(new BlockCount("stone_bricks", 1400), new BlockCount("oak_planks", 900),
            new BlockCount("oak_stairs", 240));

    private static String itemOf(String block) {
        return block.equals("water") ? null : BlockEquivalents.of(block).placedWith();
    }

    private static MaterialList<String, String> list(Map<String, Integer> needed) {
        return SharedLists.of(needed, Map.of(), CARRIED);
    }

    private static List<MaterialRows.Row<String>> rows(List<BlockCount> counts, Map<String, String> swaps, MaterialList<String, String> list) {
        return rows(counts, swaps, list, CARRIED);
    }

    private static List<MaterialRows.Row<String>> rows(List<BlockCount> counts, Map<String, String> swaps, MaterialList<String, String> list,
            Map<String, Integer> carried) {
        return MaterialRows.of(counts, swaps, KNOWN::contains, MaterialRowsTest::itemOf, Function.identity(), list,
                item -> carried.getOrDefault(item, 0));
    }

    private static List<String> blocks(List<MaterialRows.Row<String>> rows) {
        return rows.stream().map(MaterialRows.Row::block).toList();
    }

    @Test
    void unlockedRowsHaveTotalsAndTargetsMostUsedFirstWithoutNumbers() {
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("oak_stairs", 240), new BlockCount("stone_bricks", 1400),
                new BlockCount("oak_planks", 900)), Map.of("oak_stairs", "spruce_stairs"), null);
        assertEquals(List.of("stone_bricks", "oak_planks", "oak_stairs"), blocks(rows));
        assertEquals(1400, rows.get(0).total());
        assertNull(rows.get(0).target());
        assertEquals("spruce_stairs", rows.get(2).target());
        rows.forEach(row -> {
            assertNull(row.numbers());
            assertEquals(0, row.finishing());
            assertNull(row.finish());
        });
    }

    @Test
    void equalTotalsSortByBlockId() {
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("oak_stairs", 5), new BlockCount("oak_planks", 5)), Map.of(), null);
        assertEquals(List.of("oak_planks", "oak_stairs"), blocks(rows));
    }

    @Test
    void lockedRowsShowTheShownAsBlocksNumbersMostShortFirst() {
        MaterialList<String, String> list = list(Map.of("deepslate_bricks", 800, "oak_planks", 300, "spruce_stairs", 120));
        List<MaterialRows.Row<String>> rows = rows(COUNTS, Map.of("stone_bricks", "deepslate_bricks", "oak_stairs", "spruce_stairs"), list);
        assertEquals(List.of("stone_bricks", "oak_stairs", "oak_planks"), blocks(rows));
        assertEquals(new MaterialList.Row<>("deepslate_bricks", 800, 0, 800), rows.get(0).numbers());
        assertEquals(new MaterialList.Row<>("spruce_stairs", 120, 64, 56), rows.get(1).numbers());
        assertEquals(new MaterialList.Row<>("oak_planks", 300, 512, 0), rows.get(2).numbers());
    }

    @Test
    void lockedTiesFallBackToNeededThenTotal() {
        Map<String, Integer> plenty = Map.of("oak_planks", 10_000, "spruce_stairs", 10_000, "stone_bricks", 10_000);
        MaterialList<String, String> list = SharedLists.of(Map.of("oak_planks", 10, "spruce_stairs", 10, "stone_bricks", 600), Map.of(), plenty);
        List<MaterialRows.Row<String>> rows = rows(COUNTS, Map.of("oak_stairs", "spruce_stairs"), list, plenty);
        assertEquals(List.of("stone_bricks", "oak_planks", "oak_stairs"), blocks(rows));
    }

    @Test
    void aBlockWithNothingNeededShowsTheLeftoverAsHad() {
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("oak_planks", 900)), Map.of(), list(Map.of("oak_planks", 0)));
        assertEquals(new MaterialList.Row<>("oak_planks", 0, 512, 0), rows.get(0).numbers());
    }

    @Test
    void aBlockTheListDoesNotKnowShowsWhatIsCarried() {
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("oak_planks", 900)), Map.of(), list(Map.of()));
        assertEquals(new MaterialList.Row<>("oak_planks", 0, 512, 0), rows.get(0).numbers());
    }

    @Test
    void blocksShowingAsTheSameBlockShareItsNumbers() {
        MaterialList<String, String> list = list(Map.of("spruce_stairs", 200));
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("oak_stairs", 240), new BlockCount("spruce_stairs", 60)),
                Map.of("oak_stairs", "spruce_stairs"), list);
        assertEquals(rows.get(0).numbers(), rows.get(1).numbers());
        assertEquals(new MaterialList.Row<>("spruce_stairs", 200, 64, 136), rows.get(0).numbers());
    }

    @Test
    void anUnknownTargetCountsAsTheOriginalBlock() {
        MaterialList<String, String> list = list(Map.of("oak_planks", 300));
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("oak_planks", 900)), Map.of("oak_planks", "gone:planks"), list);
        assertEquals("gone:planks", rows.get(0).target());
        assertEquals(new MaterialList.Row<>("oak_planks", 300, 512, 0), rows.get(0).numbers());
    }

    @Test
    void aBlockWithoutAnItemHasNoNumbers() {
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("water", 12), new BlockCount("oak_planks", 1)), Map.of(), list(Map.of()));
        assertEquals(List.of("water", "oak_planks"), blocks(rows));
        assertNull(rows.get(0).numbers());
    }

    @Test
    void concreteAndPowderRowsHaveTheirOwnNumbers() {
        Map<String, Integer> carried = Map.of(CONCRETE, 64);
        MaterialList<String, String> list = SharedLists.of(Map.of(CONCRETE, 64, POWDER, 64), Map.of(), carried);
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount(CONCRETE, 64), new BlockCount(POWDER, 64)), Map.of(), list, carried);
        assertEquals(List.of(POWDER, CONCRETE), blocks(rows));
        assertEquals(new MaterialList.Row<>(POWDER, 64, 0, 64), rows.get(0).numbers());
        assertEquals(new MaterialList.Row<>(POWDER, 64, 64, 0), rows.get(1).numbers());
    }

    @Test
    void grassAndPathRowsHaveTheirOwnNumbers() {
        Map<String, Integer> carried = Map.of(GRASS, 10);
        MaterialList<String, String> list = SharedLists.of(Map.of(GRASS, 10, PATH, 5), Map.of(), carried);
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount(GRASS, 10), new BlockCount(PATH, 5)), Map.of(), list, carried);
        assertEquals(List.of(PATH, GRASS), blocks(rows));
        assertEquals(new MaterialList.Row<>(DIRT, 5, 0, 5), rows.get(0).numbers());
        assertEquals(new MaterialList.Row<>(DIRT, 10, 10, 0), rows.get(1).numbers());
    }

    @Test
    void aPathRowShowsItsFinishing() {
        MaterialList<String, String> list = SharedLists.of(Map.of(GRASS, 10, PATH, 5), Map.of(PATH, 15), Map.of());
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount(GRASS, 10), new BlockCount(PATH, 20)), Map.of(), list, Map.of());
        MaterialRows.Row<String> path = rows.stream().filter(row -> row.block().equals(PATH)).findFirst().orElseThrow();
        MaterialRows.Row<String> grass = rows.stream().filter(row -> row.block().equals(GRASS)).findFirst().orElseThrow();
        assertEquals(15, path.finishing());
        assertEquals(BlockEquivalents.Finish.SHOVEL, path.finish());
        assertEquals(5, path.needed());
        assertEquals(0, grass.finishing());
        assertNull(grass.finish());
        assertEquals(10, grass.needed());
    }

    @Test
    void aSwapToPathsFollowsThePathRow() {
        MaterialList<String, String> list = SharedLists.of(Map.of(PATH, 7), Map.of(PATH, 3), Map.of());
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount(GRASS, 10)), Map.of(GRASS, PATH), list, Map.of());
        assertEquals(new MaterialList.Row<>(DIRT, 7, 0, 7), rows.get(0).numbers());
        assertEquals(3, rows.get(0).finishing());
    }
}

package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.dirnei.mc.heavymatica.formats.SchematicModel.BlockCount;
import io.github.dirnei.mc.heavymatica.placement.MaterialList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MaterialRowsTest {

    private static final Set<String> KNOWN = Set.of("oak_stairs", "spruce_stairs", "oak_planks", "stone_bricks", "deepslate_bricks", "water");
    private static final Map<String, Integer> CARRIED = Map.of("oak_planks", 512, "spruce_stairs", 64, "glass", 3);

    private static final List<BlockCount> COUNTS = List.of(new BlockCount("stone_bricks", 1400), new BlockCount("oak_planks", 900),
            new BlockCount("oak_stairs", 240));

    private static String itemOf(String block) {
        return block.equals("water") ? null : block;
    }

    private static MaterialList<String, String> list(Map<String, Integer> needed) {
        return MaterialList.of(needed, Map.<String, Integer>of(), item -> CARRIED.getOrDefault(item, 0), Comparator.naturalOrder(), Comparator.naturalOrder());
    }

    private static List<MaterialRows.Row<String>> rows(List<BlockCount> counts, Map<String, String> swaps, MaterialList<String, String> list) {
        return MaterialRows.of(counts, swaps, KNOWN::contains, MaterialRowsTest::itemOf, list, item -> CARRIED.getOrDefault(item, 0));
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
        rows.forEach(row -> assertNull(row.numbers()));
    }

    @Test
    void equalTotalsSortByBlockId() {
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("oak_stairs", 5), new BlockCount("oak_planks", 5)), Map.of(), null);
        assertEquals(List.of("oak_planks", "oak_stairs"), blocks(rows));
    }

    @Test
    void lockedRowsShowTheShownAsItemsNumbersMostShortFirst() {
        MaterialList<String, String> list = list(Map.of("deepslate_bricks", 800, "oak_planks", 300, "spruce_stairs", 120));
        List<MaterialRows.Row<String>> rows = rows(COUNTS, Map.of("stone_bricks", "deepslate_bricks", "oak_stairs", "spruce_stairs"), list);
        assertEquals(List.of("stone_bricks", "oak_stairs", "oak_planks"), blocks(rows));
        assertEquals(new MaterialList.Row<>("deepslate_bricks", 800, 0, 800), rows.get(0).numbers());
        assertEquals(new MaterialList.Row<>("spruce_stairs", 120, 64, 56), rows.get(1).numbers());
        assertEquals(new MaterialList.Row<>("oak_planks", 300, 512, 0), rows.get(2).numbers());
    }

    @Test
    void lockedTiesFallBackToNeededThenTotal() {
        MaterialList<String, String> list = list(Map.of("oak_planks", 10, "spruce_stairs", 10, "stone_bricks", 600));
        List<MaterialRows.Row<String>> rows = MaterialRows.of(COUNTS, Map.of("oak_stairs", "spruce_stairs"), KNOWN::contains, MaterialRowsTest::itemOf, list,
                item -> 10_000);
        assertEquals(List.of("stone_bricks", "oak_planks", "oak_stairs"), blocks(rows));
    }

    @Test
    void anItemTheListDoesNotNeedShowsZeroNeededAndWhatIsCarried() {
        List<MaterialRows.Row<String>> rows = rows(List.of(new BlockCount("oak_planks", 900)), Map.of(), list(Map.of()));
        assertEquals(new MaterialList.Row<>("oak_planks", 0, 512, 0), rows.get(0).numbers());
    }

    @Test
    void blocksShowingAsTheSameItemShareItsNumbers() {
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
}

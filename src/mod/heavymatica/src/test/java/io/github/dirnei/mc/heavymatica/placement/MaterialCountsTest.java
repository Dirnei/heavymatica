package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MaterialCountsTest {

    private static final int PLANKS = 0;
    private static final int SLAB = 1;
    private static final int GRASS = 0;
    private static final int PATH = 1;
    private static final int WATER = 0;

    @Test
    void onlyMissingWrongBlockAndUnknownNeedAnItem() {
        Set<CellState> needing = EnumSet.of(CellState.UNKNOWN, CellState.MISSING, CellState.WRONG_BLOCK);
        for (CellState state : CellState.values()) {
            assertEquals(needing.contains(state), state.needsItem(), state.name());
        }
    }

    @Test
    void setReturnsThePreviousState() {
        ComparisonState state = new ComparisonState(2, 2, 2, new BitSet());
        assertEquals(CellState.UNKNOWN, state.set(0, 0, 0, CellState.MISSING));
        assertEquals(CellState.MISSING, state.set(0, 0, 0, CellState.MATCHING));
        assertEquals(CellState.MATCHING, state.set(0, 0, 0, CellState.MATCHING));
    }

    @Test
    void sumsOverALayerRange() {
        MaterialCounts counts = new MaterialCounts(5, 2, 1);
        for (int layer = 0; layer < 5; layer++) {
            counts.add(layer, Demand.planned(PLANKS, layer + 1));
        }
        counts.add(2, Demand.planned(SLAB, 2));
        counts.add(4, Demand.cantCarry(WATER, 1));
        counts.add(1, Demand.NONE);

        MaterialCounts.Totals middle = counts.sum(1, 3);
        assertArrayEquals(new int[] {2 + 3 + 4, 2}, middle.needed());
        assertArrayEquals(new int[] {0}, middle.cantCarry());

        MaterialCounts.Totals all = counts.sum(-3, 99);
        assertArrayEquals(new int[] {15, 2}, all.needed());
        assertArrayEquals(new int[] {1}, all.cantCarry());
    }

    @Test
    void addAndRemoveReturnToZero() {
        MaterialCounts counts = new MaterialCounts(3, 2, 1);
        Demand[] demands = {Demand.planned(PLANKS, 1), Demand.planned(SLAB, 2), Demand.cantCarry(WATER, 1), Demand.NONE};
        for (int layer = 0; layer < 3; layer++) {
            for (Demand demand : demands) {
                counts.add(layer, demand);
            }
        }
        for (int layer = 0; layer < 3; layer++) {
            for (Demand demand : demands) {
                counts.remove(layer, demand);
            }
        }
        MaterialCounts.Totals totals = counts.sum(0, 2);
        assertArrayEquals(new int[] {0, 0}, totals.needed());
        assertArrayEquals(new int[] {0}, totals.cantCarry());
    }

    @Test
    void emptyRangeSumsToZero() {
        MaterialCounts counts = new MaterialCounts(3, 1, 0);
        counts.add(1, Demand.planned(PLANKS, 4));
        assertArrayEquals(new int[] {0}, counts.sum(2, 1).needed());
        assertEquals(4, counts.sum(0, 2).needed()[PLANKS]);
        assertEquals(0, counts.sum(0, 0).needed()[PLANKS]);
    }

    @Test
    void neededIsKeptPerPlannedBlock() {
        // Grass and dirt paths are both placed with dirt, but stay apart here.
        MaterialCounts counts = new MaterialCounts(2, 2, 0);
        counts.add(0, Demand.planned(GRASS, 1));
        counts.add(1, Demand.planned(GRASS, 1));
        counts.add(1, Demand.planned(PATH, 1));
        assertArrayEquals(new int[] {2, 1}, counts.sum(0, 1).needed());
    }

    @Test
    void finishingIsSummedNextToNeededAndCantCarry() {
        MaterialCounts counts = new MaterialCounts(4, 2, 1);
        counts.add(0, Demand.planned(PATH, 5));
        counts.add(3, Demand.cantCarry(WATER, 2));
        for (int i = 0; i < 15; i++) {
            counts.addFinishing(1, PATH);
        }
        counts.addFinishing(3, GRASS);
        counts.addFinishing(3, PATH);
        counts.removeFinishing(3, PATH);

        MaterialCounts.Totals all = counts.sum(0, 3);
        assertArrayEquals(new int[] {0, 5}, all.needed());
        assertArrayEquals(new int[] {2}, all.cantCarry());
        assertArrayEquals(new int[] {1, 15}, all.finishing());
        assertArrayEquals(new int[] {0, 15}, counts.sum(0, 2).finishing());
        assertArrayEquals(new int[] {1, 0}, counts.sum(2, 3).finishing());
        assertArrayEquals(new int[] {0, 0}, counts.sum(3, 2).finishing());
    }
}

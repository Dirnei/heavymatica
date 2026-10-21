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
            counts.add(layer, Demand.item(PLANKS, layer + 1));
        }
        counts.add(2, Demand.item(SLAB, 2));
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
        Demand[] demands = {Demand.item(PLANKS, 1), Demand.item(SLAB, 2), Demand.cantCarry(WATER, 1), Demand.NONE};
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
        counts.add(1, Demand.item(PLANKS, 4));
        assertArrayEquals(new int[] {0}, counts.sum(2, 1).needed());
        assertEquals(4, counts.sum(0, 2).needed()[PLANKS]);
        assertEquals(0, counts.sum(0, 0).needed()[PLANKS]);
    }
}

package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

class ComparisonStateTest {

    /** A 10 x 10 x 10 box whose bottom five layers count toward progress. */
    private static ComparisonState halfSolid() {
        BitSet counted = new BitSet();
        counted.set(0, 500);
        return new ComparisonState(10, 10, 10, counted);
    }

    @Test
    void startsUnknownAndNotDone() {
        ComparisonState state = halfSolid();
        assertEquals(1000, state.total(CellState.UNKNOWN));
        assertEquals(0, state.matching(0, 9));
        assertEquals(500, state.counted(0, 9));
    }

    @Test
    void progressCountsMatchingCountedPositions() {
        ComparisonState state = halfSolid();
        for (int index = 0; index < 310; index++) {
            state.set(index, CellState.MATCHING);
        }
        assertEquals(310, state.matching(0, 9));
        assertEquals(500, state.counted(0, 9));
    }

    @Test
    void matchingAirDoesNotCount() {
        ComparisonState state = halfSolid();
        state.set(state.index(0, 7, 0), CellState.MATCHING);
        assertEquals(0, state.matching(0, 9));
    }

    @Test
    void extraDoesNotLowerProgress() {
        ComparisonState state = halfSolid();
        for (int index = 0; index < 310; index++) {
            state.set(index, CellState.MATCHING);
        }
        for (int index = 600; index < 640; index++) {
            state.set(index, CellState.EXTRA);
        }
        assertEquals(310, state.matching(0, 9));
        assertEquals(500, state.counted(0, 9));
        assertEquals(40, state.total(CellState.EXTRA));
    }

    @Test
    void layerProgressCountsTheVisibleLayers() {
        ComparisonState state = halfSolid();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                state.set(x, 0, z, CellState.MATCHING);
                if (x < 5) {
                    state.set(x, 1, z, CellState.MATCHING);
                }
            }
        }
        assertEquals(150, state.matching(0, 1));
        assertEquals(200, state.counted(0, 1));
        assertEquals(50, state.matching(1, 1));
        assertEquals(0, state.matching(2, 4));
    }

    @Test
    void changingAStateMovesTheTotals() {
        ComparisonState state = halfSolid();
        state.set(5, CellState.WRONG_BLOCK);
        state.set(5, CellState.MATCHING);
        assertEquals(0, state.total(CellState.WRONG_BLOCK));
        assertEquals(1, state.total(CellState.MATCHING));
        assertEquals(999, state.total(CellState.UNKNOWN));
        assertEquals(1, state.matching(0, 9));
    }

    @Test
    void maskClearsWhereTheGhostHides() {
        ComparisonState state = halfSolid();
        state.takeDirtyMask();
        state.set(9, CellState.MATCHING);
        assertEquals((byte) 0xFD, state.mask()[1]);
        assertArrayEquals(new int[] {1, 2}, state.takeDirtyMask());
        assertNull(state.takeDirtyMask());
        state.set(9, CellState.MISSING);
        assertEquals((byte) 0xFF, state.mask()[1]);
    }

    @Test
    void mistakesHideTheGhost() {
        ComparisonState state = halfSolid();
        state.set(9, CellState.WRONG_ORIENTATION);
        assertEquals((byte) 0xFD, state.mask()[1]);
    }

    @Test
    void mistakesMarkTheirBoxSection() {
        BitSet counted = new BitSet();
        ComparisonState state = new ComparisonState(40, 20, 20, counted);
        state.set(33, 17, 2, CellState.EXTRA);
        state.set(1, 1, 1, CellState.MATCHING);
        BitSet dirty = state.takeDirtyBoxSections();
        assertEquals(1, dirty.cardinality());
        assertTrue(dirty.get(state.section(33, 17, 2)));
        assertArrayEquals(new int[] {32, 16, 0}, state.sectionMin(state.section(33, 17, 2)));
        assertTrue(state.takeDirtyBoxSections().isEmpty());
    }

    @Test
    void mistakesOnASectionBorderMarkTheNeighbourToo() {
        ComparisonState state = new ComparisonState(40, 20, 20, new BitSet());
        state.set(16, 5, 5, CellState.WRONG_BLOCK);
        BitSet dirty = state.takeDirtyBoxSections();
        assertEquals(2, dirty.cardinality());
        assertTrue(dirty.get(state.section(16, 5, 5)));
        assertTrue(dirty.get(state.section(15, 5, 5)));
    }

    @Test
    void outsideReadsAsUnknown() {
        ComparisonState state = halfSolid();
        state.set(0, 0, 0, CellState.EXTRA);
        assertEquals(CellState.EXTRA, state.stateOrUnknown(0, 0, 0));
        assertEquals(CellState.UNKNOWN, state.stateOrUnknown(-1, 0, 0));
        assertEquals(CellState.UNKNOWN, state.stateOrUnknown(0, 10, 0));
    }

    @Test
    void scanningUntilEverySectionIsComparedOnce() {
        ComparisonState state = new ComparisonState(20, 10, 10, new BitSet());
        assertEquals(2, state.sectionCount());
        assertTrue(state.scanning());
        state.compared(0, true);
        assertTrue(state.scanning());
        state.compared(1, false);
        assertFalse(state.scanning());
        assertTrue(state.needsScan(1));
        assertFalse(state.needsScan(0));
    }

    @Test
    void needsFinishingHidesTheGhostAndMarksItsBoxSection() {
        ComparisonState state = new ComparisonState(40, 20, 20, new BitSet());
        state.takeDirtyMask();
        int index = state.index(1, 0, 0);
        state.set(index, CellState.NEEDS_FINISHING);
        assertEquals((byte) 0xFD, state.mask()[0]);
        assertArrayEquals(new int[] {0, 1}, state.takeDirtyMask());
        assertTrue(state.takeDirtyBoxSections().get(state.section(1, 0, 0)));
        state.set(index, CellState.MISSING);
        assertEquals((byte) 0xFF, state.mask()[0]);
        assertTrue(state.takeDirtyBoxSections().get(state.section(1, 0, 0)));
    }

    @Test
    void needsFinishingIsNotDone() {
        BitSet counted = new BitSet();
        counted.set(0, 100);
        ComparisonState state = new ComparisonState(10, 1, 10, counted);
        for (int index = 0; index < 90; index++) {
            state.set(index, CellState.MATCHING);
        }
        for (int index = 90; index < 100; index++) {
            state.set(index, CellState.NEEDS_FINISHING);
        }
        assertEquals(90, state.matching(0, 0));
        assertEquals(100, state.counted(0, 0));
        assertEquals(10, state.total(CellState.NEEDS_FINISHING));
        state.set(95, CellState.MATCHING);
        assertEquals(91, state.matching(0, 0));
        state.set(95, CellState.NEEDS_FINISHING);
        assertEquals(90, state.matching(0, 0));
    }

    @Test
    void incompleteShowsTheGhostAndABoxAndIsNotDone() {
        ComparisonState state = halfSolid();
        state.set(0, CellState.MATCHING);
        state.takeDirtyMask();
        state.takeDirtyBoxSections();
        state.set(0, CellState.INCOMPLETE, 1);
        assertEquals(0, state.matching(0, 9));
        assertTrue((state.mask()[0] & 1) != 0);
        assertArrayEquals(new int[] {0, 1}, state.takeDirtyMask());
        assertFalse(state.takeDirtyBoxSections().isEmpty());
        assertEquals(1, state.total(CellState.INCOMPLETE));
    }

    @Test
    void tooManyShowsABoxAndNoGhost() {
        ComparisonState state = halfSolid();
        state.takeDirtyBoxSections();
        state.set(0, CellState.TOO_MANY);
        assertEquals(0, state.mask()[0] & 1);
        assertFalse(state.takeDirtyBoxSections().isEmpty());
    }

    @Test
    void theFoundAmountIsKeptOnlyWhileIncomplete() {
        ComparisonState state = halfSolid();
        assertEquals(0, state.found(7));
        state.set(7, CellState.INCOMPLETE, 1);
        assertEquals(1, state.found(7));
        state.takeDirtyMask();
        assertEquals(CellState.INCOMPLETE, state.set(7, CellState.INCOMPLETE, 2));
        assertEquals(2, state.found(7));
        assertNull(state.takeDirtyMask());
        assertEquals(1, state.total(CellState.INCOMPLETE));
        state.set(7, CellState.MATCHING, 3);
        assertEquals(0, state.found(7));
        state.set(7, CellState.TOO_MANY, 4);
        assertEquals(0, state.found(7));
        state.set(7, CellState.INCOMPLETE, 1);
        state.set(7, CellState.UNKNOWN);
        assertEquals(0, state.found(7));
    }
}

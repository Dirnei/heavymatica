package io.github.dirnei.mc.heavymatica.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.ComparisonState;
import io.github.dirnei.mc.heavymatica.placement.Layers;
import io.github.dirnei.mc.heavymatica.targeting.MistakeSearch.Candidate;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

class MistakeSearchTest {

    private static final Layers.Range ALL = new Layers.Range(0, 63);

    private static ComparisonState matching(int sizeX, int sizeY, int sizeZ) {
        ComparisonState state = new ComparisonState(sizeX, sizeY, sizeZ, new BitSet());
        for (int index = 0; index < sizeX * sizeY * sizeZ; index++) {
            state.set(index, CellState.MATCHING);
        }
        return state;
    }

    private static void assertAt(Candidate candidate, int x, int y, int z, CellState state) {
        assertEquals(x, candidate.x());
        assertEquals(y, candidate.y());
        assertEquals(z, candidate.z());
        assertEquals(state, candidate.state());
    }

    @Test
    void picksTheNearestOfTwoMistakes() {
        ComparisonState state = matching(48, 8, 8);
        state.set(5, 0, 0, CellState.WRONG_BLOCK);
        state.set(40, 0, 0, CellState.WRONG_ORIENTATION);
        Candidate candidate = MistakeSearch.nearest(state, 0.5, 0.5, 0.5, PointerMode.MISTAKES, ALL);
        assertAt(candidate, 5, 0, 0, CellState.WRONG_BLOCK);
        assertEquals(5.0, candidate.distance(), 1e-9);
        assertAt(MistakeSearch.nearest(state, 47.5, 0.5, 0.5, PointerMode.MISTAKES, ALL), 40, 0, 0, CellState.WRONG_ORIENTATION);
    }

    @Test
    void missingCountsOnlyInTheModeWithMissing() {
        ComparisonState state = matching(16, 16, 16);
        state.set(3, 4, 5, CellState.MISSING);
        assertNull(MistakeSearch.nearest(state, 0, 0, 0, PointerMode.MISTAKES, ALL));
        assertAt(MistakeSearch.nearest(state, 0, 0, 0, PointerMode.MISTAKES_AND_MISSING, ALL), 3, 4, 5, CellState.MISSING);
        assertNull(MistakeSearch.nearest(state, 0, 0, 0, PointerMode.OFF, ALL));
    }

    @Test
    void everyBoxedStateIsAMistake() {
        for (CellState boxed : CellState.values()) {
            if (!boxed.boxed()) {
                continue;
            }
            ComparisonState state = matching(4, 4, 4);
            state.set(1, 1, 1, boxed);
            assertAt(MistakeSearch.nearest(state, 0, 0, 0, PointerMode.MISTAKES, ALL), 1, 1, 1, boxed);
        }
    }

    @Test
    void skipsHiddenLayers() {
        ComparisonState state = matching(8, 40, 8);
        state.set(2, 30, 2, CellState.WRONG_BLOCK);
        assertNull(MistakeSearch.nearest(state, 0, 0, 0, PointerMode.MISTAKES, new Layers.Range(0, 29)));
        assertAt(MistakeSearch.nearest(state, 0, 0, 0, PointerMode.MISTAKES, new Layers.Range(0, 30)), 2, 30, 2, CellState.WRONG_BLOCK);
        state.set(2, 17, 2, CellState.EXTRA);
        // The section of layers 16 to 31 holds both; only the visible one counts even though the hidden one is nearer.
        assertAt(MistakeSearch.nearest(state, 2.5, 35, 2.5, PointerMode.MISTAKES, new Layers.Range(0, 20)), 2, 17, 2, CellState.EXTRA);
    }

    @Test
    void findsANearerCandidateInASectionSortedLater() {
        ComparisonState state = matching(32, 16, 16);
        // The eye is inside the first section, whose only mistake is far away in its corner.
        state.set(0, 0, 15, CellState.WRONG_BLOCK);
        state.set(16, 0, 0, CellState.WRONG_BLOCK);
        assertAt(MistakeSearch.nearest(state, 15.5, 0.5, 0.5, PointerMode.MISTAKES, ALL), 16, 0, 0, CellState.WRONG_BLOCK);
    }

    @Test
    void noneWithoutCandidates() {
        assertNull(MistakeSearch.nearest(matching(20, 20, 20), 3, 3, 3, PointerMode.MISTAKES_AND_MISSING, ALL));
        assertNull(MistakeSearch.nearest(new ComparisonState(20, 20, 20, new BitSet()), 3, 3, 3, PointerMode.MISTAKES_AND_MISSING, ALL));
    }
}

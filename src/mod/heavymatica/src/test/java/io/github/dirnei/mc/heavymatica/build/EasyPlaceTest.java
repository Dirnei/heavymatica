package io.github.dirnei.mc.heavymatica.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.build.BuildRules.EasyPlaceClick;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import org.junit.jupiter.api.Test;

class EasyPlaceTest {

    /** Easy Place on, matching item on a missing ghost within reach. */
    private static final EasyPlaceClick MATCHING = new EasyPlaceClick(true, false, false, false, false, CellState.MISSING, true, true, false);

    @Test
    void matchingItemOnAGhostActs() {
        assertTrue(BuildRules.easyPlaces(MATCHING));
        assertTrue(BuildRules.easyPlaces(new EasyPlaceClick(true, false, false, false, false, CellState.INCOMPLETE, true, true, false)));
    }

    @Test
    void offDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(false, false, false, false, false, CellState.MISSING, true, true, false)));
    }

    @Test
    void sneakingDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, false, true, false, false, CellState.MISSING, true, true, false)));
    }

    @Test
    void spectatorDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, false, false, true, false, CellState.MISSING, true, true, false)));
    }

    @Test
    void remoteHeldDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, false, false, false, true, CellState.MISSING, true, true, false)));
    }

    @Test
    void screenOpenDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, true, false, false, false, CellState.MISSING, true, true, false)));
    }

    @Test
    void outOfReachDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, false, false, false, false, CellState.MISSING, false, true, false)));
    }

    @Test
    void noOrNonGhostTargetDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, false, false, false, false, null, false, false, false)));
        for (CellState state : new CellState[] {CellState.MATCHING, CellState.WRONG_BLOCK, CellState.WRONG_ORIENTATION, CellState.TOO_MANY,
                CellState.NEEDS_FINISHING, CellState.UNKNOWN, CellState.CANT_CHECK}) {
            assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, false, false, false, false, state, true, true, false)), state.name());
        }
    }

    @Test
    void wrongItemDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, false, false, false, false, CellState.MISSING, true, false, false)));
    }

    @Test
    void acceptsProgressTowardThePlannedBlockOnly() {
        assertTrue(BuildRules.easyPlaceAccepts(CellState.MATCHING));
        assertTrue(BuildRules.easyPlaceAccepts(CellState.INCOMPLETE));
        assertTrue(BuildRules.easyPlaceAccepts(CellState.NEEDS_FINISHING));
        assertTrue(BuildRules.easyPlaceAccepts(CellState.WRONG_WATER));
        assertFalse(BuildRules.easyPlaceAccepts(CellState.WRONG_BLOCK));
        assertFalse(BuildRules.easyPlaceAccepts(CellState.WRONG_ORIENTATION));
        assertFalse(BuildRules.easyPlaceAccepts(CellState.TOO_MANY));
    }

    @Test
    void theContentGoesIntoAStandingBaseWhenItMakesThePlannedBlock() {
        assertTrue(BuildRules.easyPlaceAddsContent(CellState.INCOMPLETE, CellState.MATCHING));
    }

    @Test
    void noContentStepWithoutAFillableBaseOrForAnotherContent() {
        assertFalse(BuildRules.easyPlaceAddsContent(CellState.INCOMPLETE, null));
        assertFalse(BuildRules.easyPlaceAddsContent(CellState.INCOMPLETE, CellState.WRONG_BLOCK));
        assertFalse(BuildRules.easyPlaceAddsContent(CellState.MISSING, CellState.MATCHING));
    }

    @Test
    void singleChestWaitingForItsOtherHalfDoesNotAct() {
        assertFalse(BuildRules.easyPlaces(new EasyPlaceClick(true, false, false, false, false, CellState.INCOMPLETE, true, true, true)));
    }
}

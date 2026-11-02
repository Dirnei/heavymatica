package io.github.dirnei.mc.heavymatica.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.build.BuildRules.Covering;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.placement.CellState.Expected;
import io.github.dirnei.mc.heavymatica.placement.CellState.Found;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacementRestrictionTest {

    private static final int PLANKS = 10;
    private static final int COBBLESTONE = 11;
    private static final int GRASS = 12;
    private static final int DIRT = 13;
    private static final int PATH = 14;
    private static final int SLAB = 15;
    private static final int CANDLE = 16;
    private static final int NORTH = 1;
    private static final int SOUTH = 2;

    private static boolean refused(Expected expected, Found found) {
        return BuildRules.refused(CellState.classify(expected, found));
    }

    @Test
    void matchingIsAllowed() {
        assertFalse(refused(Expected.block(PLANKS, NORTH, false), Found.block(PLANKS, NORTH, false)));
    }

    @Test
    void anEquivalentIsAllowed() {
        assertFalse(refused(Expected.block(GRASS, 0, false, new int[] {DIRT}, new int[0], -1), Found.block(DIRT, 0, false)));
    }

    @Test
    void halfOfADoubleSlabIsAllowed() {
        assertFalse(refused(Expected.block(SLAB, 0, false).withAmount(2, 0), Found.block(SLAB, 0, false, 1, 1)));
    }

    @Test
    void needsFinishingIsAllowed() {
        assertFalse(refused(Expected.block(PATH, 0, false, new int[0], new int[] {DIRT}, 0), Found.block(DIRT, 0, false)));
    }

    @Test
    void wrongWaterIsAllowed() {
        assertFalse(refused(Expected.block(PLANKS, 0, true), Found.block(PLANKS, 0, false)));
    }

    @Test
    void anUnknownPlannedBlockIsAllowed() {
        assertFalse(refused(Expected.CANT_CHECK, Found.block(COBBLESTONE, 0, false)));
        assertFalse(refused(Expected.IGNORE, Found.block(COBBLESTONE, 0, false)));
    }

    @Test
    void theEmptyBaseOfATwoItemBlockIsAllowed() {
        int pot = 20;
        int pottedFern = 21;
        int emptyPotState = 300;
        Expected planned = Expected.block(pottedFern, 0, false).withBase(emptyPotState);
        assertFalse(refused(planned, Found.block(pot, 0, false).withState(emptyPotState, -1)));
        assertTrue(refused(Expected.block(pot, 0, false), Found.block(pottedFern, 0, false).withState(301, pot)));
    }

    @Test
    void wrongBlockIsRefused() {
        assertTrue(refused(Expected.block(PLANKS, 0, false), Found.block(COBBLESTONE, 0, false)));
    }

    @Test
    void wrongOrientationIsRefused() {
        assertTrue(refused(Expected.block(PLANKS, NORTH, false), Found.block(PLANKS, SOUTH, false)));
    }

    @Test
    void theWrongSlabHalfIsRefused() {
        assertTrue(refused(Expected.block(SLAB, 0, false).withAmount(1, 2), Found.block(SLAB, 0, false, 1, 1)));
    }

    @Test
    void tooManyIsRefused() {
        assertTrue(refused(Expected.block(CANDLE, 0, false).withAmount(2, 0), Found.block(CANDLE, 0, false, 3, 0)));
    }

    @Test
    void aBlockWherePlannedAirIsRefused() {
        assertTrue(refused(Expected.AIR, Found.block(COBBLESTONE, 0, false)));
    }

    @Test
    void outsideEveryPlacementIsNotRestricted() {
        assertNull(BuildRules.deciding(List.of(), null));
    }

    @Test
    void aHiddenLayerIsNotRestricted() {
        UUID placement = UUID.randomUUID();
        assertNull(BuildRules.deciding(List.of(new Covering<>(placement, false, "air")), placement));
    }

    @Test
    void anAnchoredPlacementIsNotRestricted() {
        // Only locked placements are offered as coverings; an anchored one adds none.
        assertNull(BuildRules.deciding(List.<Covering<String>>of(), UUID.randomUUID()));
    }

    @Test
    void theSelectedPlacementDecides() {
        UUID first = UUID.randomUUID();
        UUID selected = UUID.randomUUID();
        List<Covering<String>> coverings = List.of(new Covering<>(first, true, "first"), new Covering<>(selected, true, "selected"));
        assertEquals("selected", BuildRules.deciding(coverings, selected).value());
        assertEquals("first", BuildRules.deciding(coverings, null).value());
        List<Covering<String>> selectedHidden = List.of(new Covering<>(first, true, "first"), new Covering<>(selected, false, "selected"));
        assertEquals("first", BuildRules.deciding(selectedHidden, selected).value());
    }

    @Test
    void onlyBlockItemsAndUnusedBlocksAreJudged() {
        assertTrue(BuildRules.skipsJudging(false, false, false, false));
        assertTrue(BuildRules.skipsJudging(true, true, false, false));
        assertFalse(BuildRules.skipsJudging(true, true, true, false));
        assertFalse(BuildRules.skipsJudging(true, true, false, true));
        assertFalse(BuildRules.skipsJudging(true, false, false, false));
    }
}

package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FinishesTest {

    private final Finishes finishes = new Finishes();
    private final Placement placement = PlacementTransformTest.placement(5, 5, 5, State.LOCKED);

    @Test
    void finishesWhenTheLastBlockMatches() {
        assertFalse(finishes.check(placement.id(), 199, 200, true));
        assertTrue(finishes.check(placement.id(), 200, 200, true));
    }

    @Test
    void unknownPositionsKeepItFromFinishing() {
        // Unknown positions never match, so a placement with parts unloaded stays below complete.
        assertFalse(finishes.check(placement.id(), 150, 200, false));
    }

    @Test
    void anEmptySchematicNeverFinishes() {
        assertFalse(finishes.check(placement.id(), 0, 0, true));
    }

    @Test
    void bringsTheFinishedPlacementBackWithinTenSeconds() {
        finishes.finish(placement, 1_000);
        assertTrue(finishes.canBringBack(5_000));
        assertEquals(Optional.of(placement), finishes.bringBack(5_000));
        assertFalse(finishes.canBringBack(5_000));
        assertEquals(Optional.empty(), finishes.bringBack(5_000));
    }

    @Test
    void tooLateAfterTenSeconds() {
        finishes.finish(placement, 1_000);
        assertTrue(finishes.canBringBack(11_000));
        assertFalse(finishes.canBringBack(12_000));
        assertEquals(Optional.empty(), finishes.bringBack(12_000));
    }

    @Test
    void onlyTheLatestCanBeBroughtBack() {
        Placement second = PlacementTransformTest.placement(3, 3, 3, State.LOCKED);
        finishes.finish(placement, 1_000);
        finishes.finish(second, 3_000);
        assertEquals(Optional.of(second), finishes.bringBack(4_000));
        assertEquals(Optional.empty(), finishes.bringBack(4_000));
    }

    @Test
    void aBroughtBackPlacementDoesNotFinishAgainAtOnce() {
        finishes.finish(placement, 1_000);
        finishes.bringBack(2_000);
        assertFalse(finishes.check(placement.id(), 0, 200, false));
        assertFalse(finishes.check(placement.id(), 200, 200, true));
    }

    @Test
    void itFinishesAgainAfterARepair() {
        finishes.finish(placement, 1_000);
        finishes.bringBack(2_000);
        assertFalse(finishes.check(placement.id(), 199, 200, true));
        assertTrue(finishes.check(placement.id(), 200, 200, true));
    }

    @Test
    void notBeingCompleteWhileStillScanningDoesNotRearm() {
        finishes.finish(placement, 1_000);
        finishes.bringBack(2_000);
        assertFalse(finishes.check(placement.id(), 120, 200, false));
        assertFalse(finishes.check(placement.id(), 200, 200, true));
    }

    @Test
    void clearForgetsEverything() {
        finishes.finish(placement, 1_000);
        finishes.clear();
        assertFalse(finishes.canBringBack(1_000));
    }
}

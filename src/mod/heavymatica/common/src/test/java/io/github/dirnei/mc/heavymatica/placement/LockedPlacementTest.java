package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.placement.Layers.Mode;
import io.github.dirnei.mc.heavymatica.placement.Layers.Range;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Remove;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Target;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Unlock;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Use;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import io.github.dirnei.mc.heavymatica.placement.Placements.Ray;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LockedPlacementTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final Ray SKY = new Ray(1.5, 65, 0, 0, 1, 0);

    private final Placements placements = new Placements();
    private final PlacementController controller = new PlacementController(placements);

    private Placement selected() {
        return placements.selected().orElseThrow();
    }

    /** A 12-layer placement with its bottom at y 64, anchored. */
    private Placement anchored() {
        controller.start(new PlacementSource.Local("abc"), "Gate", 3, 12, 3, OVERWORLD, new Target(1, 64, 6));
        controller.use(false, OVERWORLD, SKY);
        return selected();
    }

    private Placement locked() {
        anchored();
        controller.lockOrUnlock(0);
        return selected();
    }

    private Placement lockedFromTo(int bottom, int top) {
        Placement placement = locked();
        placements.update(placement.with(new Layers(Mode.FROM_TO, bottom, top, 1)));
        return selected();
    }

    @Test
    void lockKeyLocksAnAnchoredPlacement() {
        anchored();
        assertEquals(Optional.empty(), controller.lockOrUnlock(0));
        assertEquals(State.LOCKED, selected().state());
    }

    @Test
    void followingPlacementCannotLock() {
        controller.start(new PlacementSource.Local("abc"), "Gate", 3, 12, 3, OVERWORLD, new Target(1, 64, 6));
        controller.lockOrUnlock(0);
        assertEquals(State.FOLLOWING, selected().state());
    }

    @Test
    void unlockingNeedsASecondPressInTime() {
        locked();
        assertEquals(Optional.of(Unlock.CONFIRM), controller.lockOrUnlock(1_000));
        assertTrue(controller.unlockPending(2_000));
        assertEquals(State.LOCKED, selected().state());
        assertEquals(Optional.of(Unlock.UNLOCKED), controller.lockOrUnlock(3_500));
        assertEquals(State.ANCHORED, selected().state());
    }

    @Test
    void aSingleUnlockPressExpires() {
        locked();
        controller.lockOrUnlock(1_000);
        assertFalse(controller.unlockPending(6_000));
        assertEquals(Optional.of(Unlock.CONFIRM), controller.lockOrUnlock(6_000));
        assertEquals(State.LOCKED, selected().state());
    }

    @Test
    void lockedPlacementIgnoresMoveKeys() {
        Placement before = locked();
        controller.rotate(false);
        controller.mirror();
        controller.move(Relative.FORWARD, 0, false);
        controller.vertical(1, false);
        assertEquals(Use.NOTHING, controller.use(true, OVERWORLD, SKY));
        assertEquals(before, selected());
    }

    @Test
    void lockedPlacementCannotBeRemoved() {
        locked();
        assertEquals(Remove.NOTHING, controller.remove(1_000));
        assertEquals(Remove.NOTHING, controller.remove(1_500));
        assertEquals(State.LOCKED, selected().state());
        assertFalse(controller.removePending(1_600));
    }

    @Test
    void cyclingTheLayerSetting() {
        locked();
        controller.cycleLayers(1, 0);
        controller.cycleLayers(1, 0);
        assertEquals(Mode.FOLLOW, selected().layers().mode());
        controller.cycleLayers(1, 0);
        assertEquals(Mode.OFF, selected().layers().mode());
        controller.cycleLayers(-1, 0);
        assertEquals(Mode.FOLLOW, selected().layers().mode());
    }

    @Test
    void fromToStartsAtTheFeet() {
        // Feet at y 68 next to a placement whose bottom is at y 64: layer 4.
        locked();
        controller.cycleLayers(1, 4);
        assertEquals(new Layers(Mode.FROM_TO, 4, 4, 1), selected().layers());
        assertEquals(new Range(4, 4), Layers.visible(selected(), 4));
    }

    @Test
    void fromToBelowThePlacementStartsAtLayerZero() {
        locked();
        controller.cycleLayers(1, -3);
        assertEquals(new Layers(Mode.FROM_TO, 0, 0, 1), selected().layers());
    }

    @Test
    void fromToKeepsAnEarlierWindow() {
        lockedFromTo(4, 6);
        controller.cycleLayers(1, 0);
        controller.cycleLayers(1, 0);
        controller.cycleLayers(1, 9);
        assertEquals(new Layers(Mode.FROM_TO, 4, 6, 1), selected().layers());
    }

    @Test
    void movingTheWindowKeepsItsSize() {
        lockedFromTo(4, 6);
        controller.moveWindow(1);
        assertEquals(new Range(5, 7), Layers.visible(selected(), 0));
        controller.moveWindow(-1);
        controller.moveWindow(-1);
        assertEquals(new Range(3, 5), Layers.visible(selected(), 0));
    }

    @Test
    void theWindowStopsAtTheTopAndBottom() {
        lockedFromTo(9, 11);
        controller.moveWindow(1);
        assertEquals(new Range(9, 11), Layers.visible(selected(), 0));
        placements.update(selected().with(new Layers(Mode.FROM_TO, 0, 2, 1)));
        controller.moveWindow(-1);
        assertEquals(new Range(0, 2), Layers.visible(selected(), 0));
    }

    @Test
    void resizingGrowsAndShrinksTheTop() {
        lockedFromTo(4, 4);
        controller.resizeWindow(1);
        controller.resizeWindow(1);
        assertEquals(new Range(4, 6), Layers.visible(selected(), 0));
        controller.resizeWindow(-1);
        controller.resizeWindow(-1);
        controller.resizeWindow(-1);
        assertEquals(new Range(4, 4), Layers.visible(selected(), 0));
        placements.update(selected().with(new Layers(Mode.FROM_TO, 10, 11, 1)));
        controller.resizeWindow(1);
        assertEquals(new Range(10, 11), Layers.visible(selected(), 0));
    }

    @Test
    void followOffsetChangesWithPlusAndMinus() {
        // Feet at layer 3: default offset 1 shows 0-4, numpad + shows 0-5.
        locked();
        controller.cycleLayers(-1, 3);
        assertEquals(Mode.FOLLOW, selected().layers().mode());
        assertEquals(new Range(0, 4), Layers.visible(selected(), 3));
        controller.resizeWindow(1);
        assertEquals(new Range(0, 5), Layers.visible(selected(), 3));
        controller.resizeWindow(-1);
        controller.resizeWindow(-1);
        assertEquals(0, selected().layers().followOffset());
        assertEquals(new Range(0, 3), Layers.visible(selected(), 3));
    }

    @Test
    void followOffsetStaysWithinZeroAndSixteen() {
        locked();
        controller.cycleLayers(-1, 0);
        for (int i = 0; i < 5; i++) {
            controller.resizeWindow(-1);
        }
        assertEquals(0, selected().layers().followOffset());
        for (int i = 0; i < 30; i++) {
            controller.resizeWindow(1);
        }
        assertEquals(Layers.MAX_FOLLOW_OFFSET, selected().layers().followOffset());
    }

    @Test
    void followOffsetDoesNotChangeTheFromToWindow() {
        lockedFromTo(4, 6);
        controller.cycleLayers(1, 0);
        controller.resizeWindow(1);
        controller.cycleLayers(-1, 0);
        assertEquals(new Layers(Mode.FROM_TO, 4, 6, 2), selected().layers());
    }

    @Test
    void layerKeysNeedALock() {
        anchored();
        controller.cycleLayers(1, 4);
        controller.toggleInHand();
        assertEquals(Layers.NONE, selected().layers());
        assertFalse(selected().inHand());
    }

    @Test
    void inHandToggles() {
        locked();
        controller.toggleInHand();
        assertTrue(selected().inHand());
        controller.toggleInHand();
        assertFalse(selected().inHand());
    }

    @Test
    void unlockingShowsAllAndRelockingRestores() {
        lockedFromTo(4, 6);
        controller.toggleInHand();
        controller.lockOrUnlock(1_000);
        controller.lockOrUnlock(1_100);
        assertEquals(new Range(0, 11), Layers.visible(selected(), 0));
        controller.lockOrUnlock(2_000);
        assertEquals(new Range(4, 6), Layers.visible(selected(), 0));
        assertTrue(selected().inHand());
    }

    @Test
    void anUnlockedPlacementCanBeRemovedAgain() {
        locked();
        controller.lockOrUnlock(1_000);
        controller.lockOrUnlock(1_100);
        assertEquals(Remove.CONFIRM, controller.remove(2_000));
        assertEquals(Remove.REMOVED, controller.remove(2_100));
        assertTrue(placements.all().isEmpty());
    }

    @Test
    void lockLocksOnlyAnAnchoredPlacement() {
        controller.start(new PlacementSource.Local("abc"), "Gate", 3, 12, 3, OVERWORLD, new Target(1, 64, 6));
        controller.lock();
        assertEquals(State.FOLLOWING, selected().state());
        controller.use(false, OVERWORLD, SKY);
        controller.lock();
        assertEquals(State.LOCKED, selected().state());
    }

    @Test
    void unlockUnlocksAtOnceAndClearsAPendingPress() {
        locked();
        controller.lockOrUnlock(1_000);
        controller.unlock();
        assertEquals(State.ANCHORED, selected().state());
        assertFalse(controller.unlockPending(1_500));
        controller.unlock();
        assertEquals(State.ANCHORED, selected().state());
    }

    @Test
    void removeNowRemovesAnchoredButNotLocked() {
        locked();
        assertFalse(controller.removeNow());
        assertEquals(State.LOCKED, selected().state());
        controller.unlock();
        assertTrue(controller.removeNow());
        assertTrue(placements.selected().isEmpty());
        assertTrue(placements.all().isEmpty());
    }

    @Test
    void removeNowRemovesAFollowingPlacement() {
        controller.start(new PlacementSource.Local("abc"), "Gate", 3, 12, 3, OVERWORLD, new Target(1, 64, 6));
        assertTrue(controller.removeNow());
        assertTrue(placements.all().isEmpty());
    }

    @Test
    void setLayerModeOpensAWindowAtTheFeetTheFirstTime() {
        locked();
        controller.setLayerMode(Mode.FROM_TO, 4);
        assertEquals(new Layers(Mode.FROM_TO, 4, 4, 1), selected().layers());
        controller.setLayerMode(Mode.FOLLOW, 7);
        assertEquals(Mode.FOLLOW, selected().layers().mode());
        controller.setLayerMode(Mode.FROM_TO, 9);
        assertEquals(new Layers(Mode.FROM_TO, 4, 4, 1), selected().layers());
        controller.setLayerMode(Mode.OFF, 9);
        assertEquals(Mode.OFF, selected().layers().mode());
    }

    @Test
    void setLayerModeNeedsALock() {
        anchored();
        controller.setLayerMode(Mode.FOLLOW, 0);
        assertEquals(Mode.OFF, selected().layers().mode());
    }
}

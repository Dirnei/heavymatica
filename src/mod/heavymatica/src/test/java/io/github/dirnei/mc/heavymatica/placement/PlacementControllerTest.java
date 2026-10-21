package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Remove;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Target;
import io.github.dirnei.mc.heavymatica.placement.PlacementController.Use;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Relative;
import io.github.dirnei.mc.heavymatica.placement.Placements.Ray;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlacementControllerTest {

    private static final String OVERWORLD = "minecraft:overworld";
    /** From x 1.5, y 65, z 0 looking south (+z). */
    private static final Ray SOUTH = new Ray(1.5, 65, 0, 0, 0, 1);
    private static final Ray SKY = new Ray(1.5, 65, 0, 0, 1, 0);

    private final Placements placements = new Placements();
    private final PlacementController controller = new PlacementController(placements);

    private Placement start(String name) {
        return controller.start(new PlacementSource.Cloud("s1", 3, "abc"), name, 3, 3, 3, OVERWORLD, new Target(1, 64, 6));
    }

    private Placement selected() {
        return placements.selected().orElseThrow();
    }

    /** Starts, follows to a target south of the eye and anchors. */
    private Placement anchored(String name, int targetZ) {
        start(name);
        controller.follow(new Target(1, 64, targetZ), OVERWORLD);
        assertEquals(Use.ANCHORED, controller.use(false, OVERWORLD, SKY));
        return selected();
    }

    @Test
    void placeStartsSelectedAndFollowing() {
        Placement placement = start("Castle Gate");
        assertEquals(Optional.of(placement), placements.selected());
        assertEquals(State.FOLLOWING, placement.state());
        assertEquals(new PlacementSource.Cloud("s1", 3, "abc"), placement.source());
    }

    @Test
    void startingAgainDropsAPlacementThatWasStillFollowing() {
        start("First");
        Placement second = start("Second");
        assertEquals(1, placements.all().size());
        assertEquals(Optional.of(second), placements.selected());
    }

    @Test
    void followingTracksTheTargetAndStaysWhenThereIsNone() {
        start("Hut");
        controller.follow(new Target(10, 64, 20), OVERWORLD);
        Bounds bounds = PlacementTransform.bounds(selected());
        controller.follow(null, OVERWORLD);
        assertEquals(bounds, PlacementTransform.bounds(selected()));
        assertEquals(9, bounds.minX());
        assertEquals(64, bounds.minY());
    }

    @Test
    void rightClickAnchorsWhereverTheAim() {
        start("Hut");
        controller.follow(new Target(1, 64, 6), OVERWORLD);
        Bounds before = PlacementTransform.bounds(selected());
        assertEquals(Use.ANCHORED, controller.use(false, OVERWORLD, SKY));
        assertEquals(State.ANCHORED, selected().state());

        controller.follow(new Target(50, 70, 50), OVERWORLD);
        assertEquals(before, PlacementTransform.bounds(selected()));
    }

    @Test
    void sneakRightClickReleasesAnAnchoredPlacement() {
        anchored("Hut", 6);
        assertEquals(Use.RELEASED, controller.use(true, OVERWORLD, SKY));
        assertEquals(State.FOLLOWING, selected().state());

        controller.follow(new Target(30, 64, 30), OVERWORLD);
        assertEquals(29, PlacementTransform.bounds(selected()).minX());
    }

    @Test
    void sneakRightClickWithoutSelectionDoesNothing() {
        assertEquals(Use.NOTHING, controller.use(true, OVERWORLD, SOUTH));
    }

    @Test
    void rightClickSelectsAnotherGhost() {
        Placement gate = anchored("Gate", 6);
        Placement tower = anchored("Tower", 30);
        Bounds towerBounds = PlacementTransform.bounds(tower);

        assertEquals(Use.SELECTED, controller.use(false, OVERWORLD, SOUTH));
        assertEquals(gate.id(), selected().id());
        assertEquals(towerBounds, PlacementTransform.bounds(placements.get(tower.id()).orElseThrow()));
    }

    @Test
    void rightClickOnTheSelectedGhostChangesNothing() {
        Placement gate = anchored("Gate", 6);
        assertEquals(Use.NOTHING, controller.use(false, OVERWORLD, SOUTH));
        assertEquals(gate, selected());
    }

    @Test
    void rightClickOnNoGhostOpensTheLibrary() {
        assertEquals(Use.OPEN_LIBRARY, controller.use(false, OVERWORLD, SOUTH));
        anchored("Gate", 6);
        assertEquals(Use.OPEN_LIBRARY, controller.use(false, OVERWORLD, SKY));
    }

    @Test
    void arrowsMoveOnlyAnAnchoredPlacement() {
        start("Hut");
        controller.follow(new Target(1, 64, 6), OVERWORLD);
        Bounds following = PlacementTransform.bounds(selected());
        controller.move(Relative.FORWARD, -90, false);
        assertEquals(following, PlacementTransform.bounds(selected()));

        controller.use(false, OVERWORLD, SKY);
        // Facing roughly east, up arrow: one block east.
        controller.move(Relative.FORWARD, -85, false);
        assertEquals(following.minX() + 1, PlacementTransform.bounds(selected()).minX());
        // Shift + left while facing north: ten blocks west.
        controller.move(Relative.LEFT, 180, true);
        assertEquals(following.minX() - 9, PlacementTransform.bounds(selected()).minX());
    }

    @Test
    void upAndDown() {
        anchored("Hut", 6);
        int y = selected().y();
        controller.vertical(1, false);
        assertEquals(y + 1, selected().y());
        controller.vertical(-1, true);
        assertEquals(y - 9, selected().y());
    }

    @Test
    void rotateAndMirrorWorkInBothStates() {
        start("Hut");
        controller.rotate(false);
        controller.mirror();
        assertEquals(Placement.Turn.CLOCKWISE_90, selected().rotation());
        assertEquals(Flip.LEFT_RIGHT, selected().mirror());

        controller.use(false, OVERWORLD, SKY);
        controller.rotate(true);
        controller.mirror();
        assertEquals(Placement.Turn.NONE, selected().rotation());
        assertEquals(Flip.FRONT_BACK, selected().mirror());
        assertEquals(State.ANCHORED, selected().state());
    }

    @Test
    void removingAnAnchoredPlacementNeedsASecondPressInTime() {
        anchored("Hut", 6);
        assertEquals(Remove.CONFIRM, controller.remove(1_000));
        assertTrue(controller.removePending(2_000));
        assertEquals(Remove.REMOVED, controller.remove(3_500));
        assertEquals(Optional.empty(), placements.selected());
        assertTrue(placements.all().isEmpty());
    }

    @Test
    void aSinglePressExpires() {
        anchored("Hut", 6);
        assertEquals(Remove.CONFIRM, controller.remove(1_000));
        assertFalse(controller.removePending(6_000));
        assertEquals(Remove.CONFIRM, controller.remove(6_000));
        assertEquals(1, placements.all().size());
    }

    @Test
    void selectingAnotherPlacementCancelsThePendingRemove() {
        anchored("Gate", 6);
        Placement tower = anchored("Tower", 30);
        assertEquals(Remove.CONFIRM, controller.remove(1_000));
        controller.use(false, OVERWORLD, SOUTH);
        assertFalse(controller.removePending(1_500));
        assertEquals(Remove.CONFIRM, controller.remove(1_500));
        assertTrue(placements.get(tower.id()).isPresent());
    }

    @Test
    void aFollowingPlacementIsRemovedAtOnce() {
        start("Hut");
        assertEquals(Remove.REMOVED, controller.remove(1_000));
        assertTrue(placements.all().isEmpty());
        assertEquals(Remove.NOTHING, controller.remove(1_100));
    }
}

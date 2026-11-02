package io.github.dirnei.mc.heavymatica.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.build.PlacementSearch.Click;
import io.github.dirnei.mc.heavymatica.build.PlacementSearch.Face;
import io.github.dirnei.mc.heavymatica.build.PlacementSearch.Found;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PlacementSearchTest {

    /** A fake placed state: facing from the player's yaw, half, axis or slab type from the click. */
    private record State(String facing, String half, String axis, String slab) {
    }

    private static String facing(float yaw) {
        return switch (Math.floorMod(Math.round(yaw / 90F), 4)) {
            case 0 -> "south";
            case 1 -> "west";
            case 2 -> "north";
            default -> "east";
        };
    }

    /** Stairs as vanilla places them: facing the player's way, top half from the bottom face or a high side hit. */
    private static final Function<Click, State> STAIRS = click -> new State(facing(click.yaw()),
            click.face() == Face.DOWN || click.face().horizontal() && click.hitY() > 0.5 ? "top" : "bottom", "", "");

    private static Found<State> search(float yaw, float pitch, Function<Click, State> place, State wanted) {
        return PlacementSearch.search(PlacementSearch.candidates(yaw, pitch), place, wanted::equals);
    }

    @Test
    void theOwnRotationComesFirst() {
        Found<State> found = search(10, 20, STAIRS, new State("south", "bottom", "", ""));
        assertNotNull(found);
        assertFalse(found.click().turned());
        assertEquals(10, found.click().yaw());
        assertEquals(20, found.click().pitch());
    }

    @Test
    void aFacingAwayNeedsATurnedYaw() {
        Found<State> found = search(0, 0, STAIRS, new State("north", "top", "", ""));
        assertNotNull(found);
        assertTrue(found.click().turned());
        assertEquals(180, found.click().yaw());
        assertEquals(0, found.click().pitch());
    }

    @Test
    void aHalfCanNeedALowHit() {
        // Only side faces place, and only a hit below the middle gives the bottom half.
        Function<Click, State> sideOnly = click -> click.face().horizontal()
                ? new State(facing(click.yaw()), click.hitY() >= 0.5 ? "top" : "bottom", "", "") : null;
        Found<State> found = search(0, 0, sideOnly, new State("south", "bottom", "", ""));
        assertNotNull(found);
        assertTrue(found.click().face().horizontal());
        assertTrue(found.click().hitY() < 0.5);
        Found<State> top = search(0, 0, sideOnly, new State("south", "top", "", ""));
        assertTrue(top.click().hitY() >= 0.5);
    }

    @Test
    void anAxisComesFromTheFace() {
        Function<Click, State> log = click -> new State("", "", switch (click.face()) {
            case UP, DOWN -> "y";
            case NORTH, SOUTH -> "z";
            case WEST, EAST -> "x";
        }, "");
        Found<State> found = search(45, 0, log, new State("", "", "x", ""));
        assertNotNull(found);
        assertFalse(found.click().turned());
        assertTrue(found.click().face() == Face.WEST || found.click().face() == Face.EAST);
    }

    @Test
    void aNotEnoughYetSlabIsCompletedFromTheMatchingFace() {
        // A bottom slab is there: only a click on its top face, or the upper part of a side, adds the second half.
        Function<Click, State> slabThere = click -> click.face() == Face.UP || click.face().horizontal() && click.hitY() > 0.5
                ? new State("", "", "", "double") : null;
        Found<State> found = search(0, 0, slabThere, new State("", "", "", "double"));
        assertNotNull(found);
        assertFalse(found.click().turned());
        assertTrue(found.click().face() == Face.UP || found.click().hitY() > 0.5);
    }

    @Test
    void noCandidateGivesNothing() {
        assertNull(search(0, 0, click -> null, new State("south", "bottom", "", "")));
        assertNull(search(0, 0, STAIRS, new State("up", "bottom", "", "")));
    }

    @Test
    void candidatesStayOnTheTargetBlock() {
        List<Click> clicks = PlacementSearch.candidates(30, 10);
        for (Click click : clicks) {
            for (double coordinate : new double[] {click.hitX(), click.hitY(), click.hitZ()}) {
                assertTrue(coordinate >= 0 && coordinate <= 1, click.toString());
            }
        }
        assertEquals(7 * (2 + 4 * 3), clicks.size());
    }

    @Test
    void theContentClickHitsAFaceTheEyeSees() {
        Click above = PlacementSearch.visibleFace(0.5, 2.6, -1, 12, 60);
        assertEquals(Face.UP, above.face());
        assertEquals(1, above.hitY());
        assertFalse(above.turned());
        assertEquals(12, above.yaw());
        Click side = PlacementSearch.visibleFace(0.5, 0.6, -3, 0, 0);
        assertEquals(Face.NORTH, side.face());
        assertEquals(0, side.hitZ());
        assertEquals(0.5, side.hitY());
        assertEquals(Face.EAST, PlacementSearch.visibleFace(4, 1.5, 0.5, 0, 0).face());
        assertEquals(Face.DOWN, PlacementSearch.visibleFace(0.5, -2, 0.5, 0, 0).face());
        assertEquals(Face.UP, PlacementSearch.visibleFace(0.5, 0.5, 0.5, 0, 0).face());
    }
}

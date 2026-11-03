package io.github.dirnei.mc.heavymatica.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dirnei.mc.heavymatica.placement.Layers.Mode;
import io.github.dirnei.mc.heavymatica.placement.Layers.Range;
import io.github.dirnei.mc.heavymatica.placement.Placement.State;
import org.junit.jupiter.api.Test;

class LayersTest {

    private static Placement twelveHigh(State state, Layers layers) {
        return PlacementTransformTest.placement(3, 12, 3, state).with(state, 0).with(layers);
    }

    @Test
    void unlockedShowsAllLayers() {
        Placement anchored = twelveHigh(State.ANCHORED, new Layers(Mode.FROM_TO, 4, 6, 1));
        assertEquals(new Range(0, 11), Layers.visible(anchored, 3));
    }

    @Test
    void offShowsAllLayers() {
        assertEquals(new Range(0, 11), Layers.visible(twelveHigh(State.LOCKED, Layers.NONE), 3));
    }

    @Test
    void fromToShowsTheWindow() {
        assertEquals(new Range(4, 6), Layers.visible(twelveHigh(State.LOCKED, new Layers(Mode.FROM_TO, 4, 6, 1)), 0));
    }

    @Test
    void fromToWithoutAWindowShowsAll() {
        assertEquals(new Range(0, 11), Layers.visible(twelveHigh(State.LOCKED, new Layers(Mode.FROM_TO, -1, -1, 1)), 0));
    }

    @Test
    void followShowsUpToFeetPlusOffset() {
        Placement locked = twelveHigh(State.LOCKED, new Layers(Mode.FOLLOW, -1, -1, 1));
        assertEquals(new Range(0, 4), Layers.visible(locked, 3));
        assertEquals(new Range(0, 3), Layers.visible(locked.with(locked.layers().withFollowOffset(0)), 3));
    }

    @Test
    void followClampsIntoThePlacement() {
        Placement locked = twelveHigh(State.LOCKED, new Layers(Mode.FOLLOW, -1, -1, 1));
        assertEquals(new Range(0, 0), Layers.visible(locked, -5));
        assertEquals(new Range(0, 11), Layers.visible(locked, 40));
    }

    @Test
    void layersCountFromTheBottom() {
        // A 12-high placement with its bottom at y 64: layer 0 is y 64, layer 11 is y 75.
        Placement locked = twelveHigh(State.LOCKED, new Layers(Mode.FOLLOW, -1, -1, 1));
        assertEquals(64, locked.y());
        assertEquals(new Range(0, 11), Layers.visible(locked.with(locked.layers().withFollowOffset(0)), 75 - locked.y()));
    }
}

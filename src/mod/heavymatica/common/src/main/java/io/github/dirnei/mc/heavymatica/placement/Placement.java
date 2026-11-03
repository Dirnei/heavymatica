package io.github.dirnei.mc.heavymatica.placement;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One schematic version put into the world. Positions are stored as a doubled pivot:
 * twice the centre of the footprint on x and z, and the bottom y, so rotating around the centre never drifts. Bounds
 * are derived through {@link PlacementTransform}.
 *
 * @param name         the library name, shown in the overlay
 * @param width        schematic size on x before rotation
 * @param height       schematic size on y
 * @param length       schematic size on z before rotation
 * @param pivotX2      twice the footprint centre on x
 * @param y            the bottom layer's y
 * @param pivotZ2      twice the footprint centre on z
 * @param dimension    the dimension ID, for example {@code minecraft:overworld}
 * @param followOffset while FOLLOWING, how far above (or below) the crosshair target the ghost sits
 * @param layers       which layers show while LOCKED; kept when unlocked
 * @param inHand       while LOCKED, show only blocks matching the held and hotbar items
 * @param swaps        original block ID to the block ID it shows as, sorted by original
 */
public record Placement(UUID id, PlacementSource source, String name, int width, int height, int length, int pivotX2, int y, int pivotZ2,
                        Turn rotation, Flip mirror, String dimension, State state, int followOffset, Layers layers, boolean inHand,
                        Map<String, String> swaps) {

    public Placement {
        swaps = Collections.unmodifiableSortedMap(new TreeMap<>(swaps));
    }

    public enum State {
        FOLLOWING,
        ANCHORED,
        LOCKED,
    }

    /** Rotation in the order of vanilla's {@code Rotation}. */
    public enum Turn {
        NONE(0),
        CLOCKWISE_90(90),
        CLOCKWISE_180(180),
        COUNTERCLOCKWISE_90(270);

        private final int degrees;

        Turn(int degrees) {
            this.degrees = degrees;
        }

        public int degrees() {
            return degrees;
        }

        public Turn clockwise() {
            return values()[(ordinal() + 1) % 4];
        }

        public Turn counterClockwise() {
            return values()[(ordinal() + 3) % 4];
        }

        /** True for 90 and 270 degrees, which swap width and length. */
        public boolean swapsAxes() {
            return this == CLOCKWISE_90 || this == COUNTERCLOCKWISE_90;
        }
    }

    /** Mirror in the order of vanilla's {@code Mirror}: LEFT_RIGHT flips z, FRONT_BACK flips x (schematic-local). */
    public enum Flip {
        NONE,
        LEFT_RIGHT,
        FRONT_BACK;

        public Flip next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public boolean following() {
        return state == State.FOLLOWING;
    }

    public boolean locked() {
        return state == State.LOCKED;
    }

    Placement with(int pivotX2, int y, int pivotZ2) {
        return new Placement(id, source, name, width, height, length, pivotX2, y, pivotZ2, rotation, mirror, dimension, state, followOffset, layers, inHand, swaps);
    }

    Placement with(Turn rotation, Flip mirror) {
        return new Placement(id, source, name, width, height, length, pivotX2, y, pivotZ2, rotation, mirror, dimension, state, followOffset, layers, inHand, swaps);
    }

    Placement with(State state, int followOffset) {
        return new Placement(id, source, name, width, height, length, pivotX2, y, pivotZ2, rotation, mirror, dimension, state, followOffset, layers, inHand, swaps);
    }

    Placement inDimension(String dimension) {
        return new Placement(id, source, name, width, height, length, pivotX2, y, pivotZ2, rotation, mirror, dimension, state, followOffset, layers, inHand, swaps);
    }

    Placement with(Layers layers) {
        return new Placement(id, source, name, width, height, length, pivotX2, y, pivotZ2, rotation, mirror, dimension, state, followOffset, layers, inHand, swaps);
    }

    Placement withInHand(boolean inHand) {
        return new Placement(id, source, name, width, height, length, pivotX2, y, pivotZ2, rotation, mirror, dimension, state, followOffset, layers, inHand, swaps);
    }

    Placement withSwaps(Map<String, String> swaps) {
        return new Placement(id, source, name, width, height, length, pivotX2, y, pivotZ2, rotation, mirror, dimension, state, followOffset, layers, inHand, swaps);
    }
}

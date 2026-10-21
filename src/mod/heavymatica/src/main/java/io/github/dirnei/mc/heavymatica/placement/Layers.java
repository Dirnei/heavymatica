package io.github.dirnei.mc.heavymatica.placement;

/**
 * Which horizontal layers of a locked placement show. Layers count from 0 at the placement's bottom.
 *
 * @param bottom       first layer of the from-to window, or -1 while the window has never been set
 * @param top          last layer of the from-to window, inclusive
 * @param followOffset in follow feet, how many layers above the feet layer still show; 0 is the layer stood on
 */
public record Layers(Mode mode, int bottom, int top, int followOffset) {

    public static final int DEFAULT_FOLLOW_OFFSET = 1;
    public static final int MAX_FOLLOW_OFFSET = 16;

    public enum Mode {
        OFF,
        FROM_TO,
        FOLLOW;

        Mode step(int direction) {
            return values()[Math.floorMod(ordinal() + direction, values().length)];
        }
    }

    /** An inclusive range of layers. */
    public record Range(int from, int to) {

        public boolean contains(int layer) {
            return layer >= from && layer <= to;
        }
    }

    public static final Layers NONE = new Layers(Mode.OFF, -1, -1, DEFAULT_FOLLOW_OFFSET);

    public Layers withMode(Mode mode) {
        return new Layers(mode, bottom, top, followOffset);
    }

    public Layers withWindow(int bottom, int top) {
        return new Layers(mode, bottom, top, followOffset);
    }

    public Layers withFollowOffset(int followOffset) {
        return new Layers(mode, bottom, top, Math.max(0, Math.min(followOffset, MAX_FOLLOW_OFFSET)));
    }

    public boolean windowSet() {
        return bottom >= 0;
    }

    /**
     * The layers that show. An unlocked placement and the OFF mode show all layers; FOLLOW shows layer 0 up to the
     * feet layer plus the follow offset.
     *
     * @param feetLayer the player's feet y minus the placement's bottom y
     */
    public static Range visible(Placement placement, int feetLayer) {
        int last = placement.height() - 1;
        Layers layers = placement.layers();
        if (!placement.locked()) {
            return new Range(0, last);
        }
        return switch (layers.mode()) {
            case OFF -> new Range(0, last);
            case FROM_TO -> layers.windowSet() ? new Range(clamp(layers.bottom(), last), clamp(layers.top(), last)) : new Range(0, last);
            case FOLLOW -> new Range(0, clamp(feetLayer + layers.followOffset(), last));
        };
    }

    static int clamp(int layer, int last) {
        return Math.max(0, Math.min(layer, last));
    }
}

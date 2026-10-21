package io.github.dirnei.mc.heavymatica.placement;

/** How one position of a locked placement compares with the world. Stored as its ordinal in one byte per cell. */
public enum CellState {
    /** Not compared yet, or in a chunk that is not loaded. */
    UNKNOWN,
    /** Structure void or a block the game does not know. */
    IGNORED,
    MATCHING,
    MISSING,
    WRONG_BLOCK,
    WRONG_ORIENTATION,
    WRONG_WATER,
    EXTRA;

    private static final CellState[] VALUES = values();

    static CellState of(byte stored) {
        return VALUES[stored];
    }

    /** True for the states that draw a ghost: missing and not yet known. */
    public boolean showsGhost() {
        return this == UNKNOWN || this == MISSING;
    }

    /** True for the states whose position still needs its item: missing, wrong block and not yet known. */
    public boolean needsItem() {
        return this == UNKNOWN || this == MISSING || this == WRONG_BLOCK;
    }

    /** True for the states marked with a coloured box. */
    public boolean mistake() {
        return this == WRONG_BLOCK || this == WRONG_ORIENTATION || this == WRONG_WATER || this == EXTRA;
    }

    /**
     * What a schematic position holds, reduced to what comparison looks at.
     *
     * @param block       a block identity; equal blocks have equal values
     * @param orientation the values of the orientation properties; equal orientations have equal values
     */
    public record Expected(Kind kind, int block, int orientation, boolean waterlogged) {

        public enum Kind {
            IGNORED,
            AIR,
            BLOCK,
        }

        public static final Expected IGNORE = new Expected(Kind.IGNORED, 0, 0, false);
        public static final Expected AIR = new Expected(Kind.AIR, 0, 0, false);

        public static Expected block(int block, int orientation, boolean waterlogged) {
            return new Expected(Kind.BLOCK, block, orientation, waterlogged);
        }

        /** True when the position counts toward progress. */
        public boolean counted() {
            return kind == Kind.BLOCK;
        }
    }

    /** What the world holds at a position, in the same terms as {@link Expected}. */
    public record Found(boolean air, int block, int orientation, boolean waterlogged) {

        public static final Found NOTHING = new Found(true, 0, 0, false);

        public static Found block(int block, int orientation, boolean waterlogged) {
            return new Found(false, block, orientation, waterlogged);
        }
    }

    /** The state of a position; when a block differs in several ways, the first fix decides. */
    public static CellState classify(Expected expected, Found found) {
        return switch (expected.kind()) {
            case IGNORED -> IGNORED;
            case AIR -> found.air() ? MATCHING : EXTRA;
            case BLOCK -> {
                if (found.air()) {
                    yield MISSING;
                }
                if (found.block() != expected.block()) {
                    yield WRONG_BLOCK;
                }
                if (found.orientation() != expected.orientation()) {
                    yield WRONG_ORIENTATION;
                }
                yield found.waterlogged() == expected.waterlogged() ? MATCHING : WRONG_WATER;
            }
        };
    }
}

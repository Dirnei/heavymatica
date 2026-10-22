package io.github.dirnei.mc.heavymatica.placement;

import java.util.Arrays;

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
    EXTRA,
    /** A block that becomes the planned one after a finishing step, such as dirt under a planned dirt path. */
    NEEDS_FINISHING;

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

    /** True for the mistakes: wrong block, wrong orientation, wrong water and extra. */
    public boolean mistake() {
        return this == WRONG_BLOCK || this == WRONG_ORIENTATION || this == WRONG_WATER || this == EXTRA;
    }

    /** True for the states marked with a coloured box: the mistakes and needs finishing. */
    public boolean boxed() {
        return mistake() || this == NEEDS_FINISHING;
    }

    /**
     * What a schematic position holds, reduced to what comparison looks at.
     *
     * @param block       a block identity; equal blocks have equal values
     * @param orientation the values of the orientation properties; equal orientations have equal values
     * @param same        sorted blocks that count as this block
     * @param finishFrom  sorted blocks that become this block after the finishing step
     * @param finish      the finishing step's ordinal, or -1 without one
     */
    public record Expected(Kind kind, int block, int orientation, boolean waterlogged, int[] same, int[] finishFrom, int finish) {

        public enum Kind {
            IGNORED,
            AIR,
            BLOCK,
        }

        private static final int[] NONE = {};

        public static final Expected IGNORE = new Expected(Kind.IGNORED, 0, 0, false, NONE, NONE, -1);
        public static final Expected AIR = new Expected(Kind.AIR, 0, 0, false, NONE, NONE, -1);

        public static Expected block(int block, int orientation, boolean waterlogged) {
            return block(block, orientation, waterlogged, NONE, NONE, -1);
        }

        public static Expected block(int block, int orientation, boolean waterlogged, int[] same, int[] finishFrom, int finish) {
            return new Expected(Kind.BLOCK, block, orientation, waterlogged, same, finishFrom, finish);
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
                CellState done;
                if (found.block() == expected.block() || Arrays.binarySearch(expected.same(), found.block()) >= 0) {
                    done = MATCHING;
                } else if (Arrays.binarySearch(expected.finishFrom(), found.block()) >= 0) {
                    done = NEEDS_FINISHING;
                } else {
                    yield WRONG_BLOCK;
                }
                if (found.orientation() != expected.orientation()) {
                    yield WRONG_ORIENTATION;
                }
                yield found.waterlogged() == expected.waterlogged() ? done : WRONG_WATER;
            }
        };
    }
}

package io.github.dirnei.mc.heavymatica.targeting;

import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents.Finish;
import io.github.dirnei.mc.heavymatica.placement.CellState;
import io.github.dirnei.mc.heavymatica.remote.PlacementOverlay;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** The state line of a ghost target, in the overlay's comparison colours. */
public final class TargetLines {

    public static final int TEXT = PlacementOverlay.TEXT;
    public static final int MUTED = PlacementOverlay.MUTED;
    public static final int RED = PlacementOverlay.MISTAKE_RED;
    public static final int ORANGE = PlacementOverlay.MISTAKE_ORANGE;
    public static final int CYAN = PlacementOverlay.MISTAKE_CYAN;
    public static final int YELLOW = PlacementOverlay.FINISHING_YELLOW;

    /**
     * A translation key and its colour. The wrong block line takes the found block's name, the wrong orientation line
     * the found block's orientation, the incomplete line how many more, found and planned, the too many line found and
     * planned.
     */
    public record StateLine(String key, int color) {
    }

    private TargetLines() {
    }

    /** The line for a target's state; null for states that are never targets. */
    public static @Nullable StateLine state(CellState state, @Nullable Finish finish, boolean plannedWaterlogged) {
        return switch (state) {
            case MISSING -> new StateLine("heavymatica.target.missing", TEXT);
            case UNKNOWN -> new StateLine("heavymatica.target.unknown", MUTED);
            case WRONG_BLOCK -> new StateLine("heavymatica.target.wrong_block", RED);
            case WRONG_ORIENTATION -> new StateLine("heavymatica.target.wrong_orientation", ORANGE);
            case TOO_MANY -> new StateLine("heavymatica.target.too_many", ORANGE);
            case INCOMPLETE -> new StateLine("heavymatica.target.incomplete", TEXT);
            case WRONG_WATER -> new StateLine(plannedWaterlogged ? "heavymatica.target.needs_water" : "heavymatica.target.not_waterlogged", CYAN);
            case NEEDS_FINISHING -> new StateLine(finish == null ? "heavymatica.target.finish"
                    : "heavymatica.target.finish." + finish.name().toLowerCase(Locale.ROOT), YELLOW);
            case MATCHING, IGNORED, EXTRA -> null;
        };
    }
}

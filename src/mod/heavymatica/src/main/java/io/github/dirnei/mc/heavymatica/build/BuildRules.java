package io.github.dirnei.mc.heavymatica.build;

import io.github.dirnei.mc.heavymatica.placement.CellState;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** The decisions of Easy Place and placement restriction, free of the game. */
public final class BuildRules {

    /**
     * What the player and the ghost under the crosshair look like as a right-click starts.
     *
     * @param waitsForOtherHalf true when the target is a single chest where one half of a double chest is planned
     */
    public record EasyPlaceClick(boolean on, boolean screenOpen, boolean spectator, boolean remoteHeld,
                                 @Nullable CellState target, boolean inReach, boolean heldIsCandidate, boolean waitsForOtherHalf) {
    }

    /** A locked placement in the player's dimension with the position inside it, and whether its layers show it. */
    public record Covering<T>(UUID placement, boolean inVisibleLayers, T value) {
    }

    private BuildRules() {
    }

    /**
     * True for an Easy Place click, sneaking or not: Easy Place on, no screen, not a spectator, no remote held, and a
     * missing or not-enough-yet ghost within reach whose candidate items include the held one. A single chest waiting
     * for its other half is no target: the chest a click places there lands beside it.
     */
    public static boolean easyPlaces(EasyPlaceClick click) {
        return click.on() && !click.screenOpen() && !click.spectator() && !click.remoteHeld()
                && (click.target() == CellState.MISSING || click.target() == CellState.INCOMPLETE) && click.inReach() && click.heldIsCandidate()
                && !click.waitsForOtherHalf();
    }

    /**
     * The placement that decides a position: the selected one when it covers the position in its visible layers,
     * otherwise the first that does; null when the position is not restricted.
     */
    public static <T> @Nullable Covering<T> deciding(List<Covering<T>> coverings, @Nullable UUID selected) {
        Covering<T> first = null;
        for (Covering<T> covering : coverings) {
            if (!covering.inVisibleLayers()) {
                continue;
            }
            if (covering.placement().equals(selected)) {
                return covering;
            }
            if (first == null) {
                first = covering;
            }
        }
        return first;
    }

    /**
     * True when the comparison's verdict on the block a click would place refuses it: wrong block, wrong orientation,
     * too many or extra. Wrong water passes, since water is not what the click places.
     */
    public static boolean refused(CellState judged) {
        return judged == CellState.WRONG_BLOCK || judged == CellState.WRONG_ORIENTATION || judged == CellState.TOO_MANY || judged == CellState.EXTRA;
    }

    /**
     * True when Easy Place accepts the state a candidate click places: the planned block, or one that counts toward it,
     * in the planned orientation with no more than the planned amount. Waterlogging is left to the world.
     */
    public static boolean easyPlaceAccepts(CellState judged) {
        return judged == CellState.MATCHING || judged == CellState.INCOMPLETE || judged == CellState.NEEDS_FINISHING || judged == CellState.WRONG_WATER;
    }

    /**
     * True when an Easy Place click puts the held content into the base standing at a not-enough-yet target instead of
     * placing a block.
     *
     * @param filled the comparison's verdict on the two-item block the held item makes of the standing block, or null
     *               when it makes none
     */
    public static boolean easyPlaceAddsContent(CellState target, @Nullable CellState filled) {
        return target == CellState.INCOMPLETE && filled != null && easyPlaceAccepts(filled);
    }

    /**
     * True when restriction judges a click as the two-item block it makes of the clicked block: the held item is the
     * content of the clicked base and the player does not sneak, so the base takes it in, whatever the item would
     * otherwise place.
     */
    public static boolean judgesAsFilled(boolean fillsClicked, boolean sneaking) {
        return fillsClicked && !sneaking;
    }

    /**
     * True when restriction leaves a click to vanilla before judging it: an item that places no block, or a block that
     * acts when used while the player does not sneak, unless the click adds to that very block (a candle onto candles).
     */
    public static boolean skipsJudging(boolean blockItem, boolean clickedActsOnUse, boolean sneaking, boolean addsToClicked) {
        return !blockItem || clickedActsOnUse && !sneaking && !addsToClicked;
    }
}

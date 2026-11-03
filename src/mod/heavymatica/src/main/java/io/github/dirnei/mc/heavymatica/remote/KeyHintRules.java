package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.placement.Layers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Which key hints the corner list shows, and where, free of the game. */
public final class KeyHintRules {

    /** Space between the list and the screen's edge. */
    static final int MARGIN = 4;
    /** The list's bottom above the screen's bottom when lifted: four subtitle rows above vanilla's lowest one at 30. */
    static final int LIFTED = 30 + 4 * 10 + MARGIN;
    /** The hotbar's right edge from the screen's centre, plus room for an offhand slot. */
    private static final int HOTBAR_RIGHT = 91 + 29;

    public enum Hint {
        SELECT,
        ANCHOR,
        MOVE,
        UP_DOWN,
        ROTATE,
        MIRROR,
        RELEASE,
        MENU,
        DESELECT,
        LOCK,
        UNLOCK,
        REMOVE,
        CANCEL,
        MATERIALS,
        IN_HAND,
        CYCLE_LAYERS,
        WINDOW,
        WINDOW_SIZE,
        FOLLOW_HEIGHT,
        REMOVE_AGAIN,
        UNLOCK_AGAIN,
        CORNER1,
        CORNER2,
        MOVE_CORNER_CLICK,
        MOVE_CORNER,
        CORNER_UP_DOWN,
        SWAP_CORNER,
        CAPTURE_MENU,
    }

    /** Wheel actions that only get a hint when the player bound a key for them. */
    public enum WheelKey {
        LOCK,
        REMOVE,
        MATERIALS,
        IN_HAND,
        CYCLE_LAYERS,
    }

    public enum Selected {
        NONE,
        FOLLOWING,
        ANCHORED,
        LOCKED,
    }

    /**
     * What the hints depend on.
     *
     * @param corners     the capture box's set corners, 0 to 2
     * @param captureBusy true while a capture reads or saves
     * @param bound       the wheel actions with a bound key
     */
    public record Situation(boolean remoteHeld, boolean capturing, int corners, boolean captureBusy, Selected selected, Layers.Mode layers,
                            boolean removePending, boolean unlockPending, Set<WheelKey> bound) {
    }

    private KeyHintRules() {
    }

    /** True when the list starts with the layer header: a locked placement is selected outside capture mode. */
    public static boolean header(Situation situation) {
        return !situation.capturing() && situation.selected() == Selected.LOCKED;
    }

    /** The hints in the order they show. Without the remote only a locked placement's layer hints show. */
    public static List<Hint> hints(Situation situation) {
        List<Hint> hints = new ArrayList<>();
        if (situation.capturing()) {
            if (situation.remoteHeld() && !situation.captureBusy()) {
                captureHints(situation.corners(), hints);
            }
            return hints;
        }
        if (!situation.remoteHeld()) {
            if (situation.selected() == Selected.LOCKED) {
                layerHints(situation, hints);
            }
            return hints;
        }
        switch (situation.selected()) {
            case NONE -> {
                hints.add(Hint.SELECT);
                hints.add(Hint.MENU);
            }
            case LOCKED -> {
                if (pending(situation, hints)) {
                    return hints;
                }
                layerHints(situation, hints);
                hints.add(Hint.MENU);
                hints.add(Hint.DESELECT);
                bound(situation, WheelKey.IN_HAND, Hint.IN_HAND, hints);
                bound(situation, WheelKey.MATERIALS, Hint.MATERIALS, hints);
                bound(situation, WheelKey.LOCK, Hint.UNLOCK, hints);
            }
            case FOLLOWING -> {
                hints.add(Hint.ANCHOR);
                hints.add(Hint.UP_DOWN);
                hints.add(Hint.ROTATE);
                hints.add(Hint.MIRROR);
                hints.add(Hint.MENU);
                bound(situation, WheelKey.REMOVE, Hint.CANCEL, hints);
                bound(situation, WheelKey.MATERIALS, Hint.MATERIALS, hints);
            }
            case ANCHORED -> {
                if (pending(situation, hints)) {
                    return hints;
                }
                hints.add(Hint.MOVE);
                hints.add(Hint.UP_DOWN);
                hints.add(Hint.ROTATE);
                hints.add(Hint.MIRROR);
                hints.add(Hint.RELEASE);
                hints.add(Hint.MENU);
                hints.add(Hint.DESELECT);
                bound(situation, WheelKey.LOCK, Hint.LOCK, hints);
                bound(situation, WheelKey.REMOVE, Hint.REMOVE, hints);
                bound(situation, WheelKey.MATERIALS, Hint.MATERIALS, hints);
            }
        }
        return hints;
    }

    private static boolean pending(Situation situation, List<Hint> hints) {
        if (situation.removePending()) {
            hints.add(Hint.REMOVE_AGAIN);
            return true;
        }
        if (situation.unlockPending()) {
            hints.add(Hint.UNLOCK_AGAIN);
            return true;
        }
        return false;
    }

    private static void layerHints(Situation situation, List<Hint> hints) {
        switch (situation.layers()) {
            case FROM_TO -> {
                hints.add(Hint.WINDOW);
                hints.add(Hint.WINDOW_SIZE);
            }
            case FOLLOW -> hints.add(Hint.FOLLOW_HEIGHT);
            case OFF -> {
            }
        }
        bound(situation, WheelKey.CYCLE_LAYERS, Hint.CYCLE_LAYERS, hints);
    }

    private static void captureHints(int corners, List<Hint> hints) {
        hints.add(corners == 0 ? Hint.CORNER1 : corners == 1 ? Hint.CORNER2 : Hint.MOVE_CORNER_CLICK);
        if (corners > 0) {
            hints.add(Hint.MOVE_CORNER);
            hints.add(Hint.CORNER_UP_DOWN);
        }
        if (corners == 2) {
            hints.add(Hint.SWAP_CORNER);
        }
        hints.add(Hint.CAPTURE_MENU);
    }

    private static void bound(Situation situation, WheelKey key, Hint hint, List<Hint> hints) {
        if (situation.bound().contains(key)) {
            hints.add(hint);
        }
    }

    /**
     * True while the list shows: the HUD visible, no screen open and key hints on, with the remote held, or pinned
     * with a locked placement selected outside capture mode; and there is something to list.
     */
    public static boolean shows(boolean hideGui, boolean screenOpen, boolean keyHints, boolean pinned, Situation situation) {
        if (hideGui || screenOpen || !keyHints) {
            return false;
        }
        if (!situation.remoteHeld() && !(pinned && header(situation))) {
            return false;
        }
        return header(situation) || !hints(situation).isEmpty();
    }

    /**
     * The y of the list's bottom edge: near the screen's bottom, or lifted above the subtitle rows while subtitles are
     * on, and above the hotbar when the list would reach it.
     */
    public static int bottom(int guiWidth, int guiHeight, int listWidth, boolean subtitles) {
        int left = guiWidth - MARGIN - listWidth;
        boolean besideHotbar = left >= guiWidth / 2 + HOTBAR_RIGHT + MARGIN;
        return subtitles || !besideHotbar ? guiHeight - LIFTED : guiHeight - MARGIN;
    }

    /** The key hints setting after carrying over the former compact overlay, which hid them. */
    public static boolean keyHintsAfterMigration(boolean compact, boolean keyHints) {
        return !compact && keyHints;
    }
}

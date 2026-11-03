package io.github.dirnei.mc.heavymatica.remote;

/** When the placement overlay shows, free of the game. */
public final class OverlayRules {

    private OverlayRules() {
    }

    /**
     * True while the overlay shows: the HUD visible and no screen open, and either the remote held, or the overlay pinned
     * with a placement selected outside capture mode.
     */
    public static boolean shows(boolean hideGui, boolean screenOpen, boolean remoteHeld, boolean pinned, boolean selected, boolean capturing) {
        if (hideGui || screenOpen) {
            return false;
        }
        return remoteHeld || pinned && selected && !capturing;
    }
}

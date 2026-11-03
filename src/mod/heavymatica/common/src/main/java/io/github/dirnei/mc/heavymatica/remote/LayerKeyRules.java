package io.github.dirnei.mc.heavymatica.remote;

/** When the layer keys and the wheel keys act, free of the game. */
public final class LayerKeyRules {

    private LayerKeyRules() {
    }

    /** True while the layer keys act: no screen open, and a remote held or the selected placement locked. */
    public static boolean active(boolean screenOpen, boolean remoteHeld, boolean lockedSelected) {
        return !screenOpen && (remoteHeld || lockedSelected);
    }

    /** True while the wheel key and the wheel action keys act: no screen open, and a remote held or a placement selected outside capture mode. */
    public static boolean wheelActive(boolean screenOpen, boolean remoteHeld, boolean selected, boolean capturing) {
        return !screenOpen && wheelAllowed(remoteHeld, selected, capturing);
    }

    /** True while an open wheel may stay open: a remote held, or a placement selected outside capture mode. */
    public static boolean wheelAllowed(boolean remoteHeld, boolean selected, boolean capturing) {
        return remoteHeld || selected && !capturing;
    }
}

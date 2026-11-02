package io.github.dirnei.mc.heavymatica.remote;

/** When the layer keys act, free of the game. */
public final class LayerKeyRules {

    private LayerKeyRules() {
    }

    /** True while the layer keys act: no screen open, and a remote held or the selected placement locked. */
    public static boolean active(boolean screenOpen, boolean remoteHeld, boolean lockedSelected) {
        return !screenOpen && (remoteHeld || lockedSelected);
    }

    /** True when the layer keys act although no remote is held, so only the layer handling runs. */
    public static boolean withoutRemote(boolean screenOpen, boolean remoteHeld, boolean lockedSelected, boolean capturing) {
        return !screenOpen && !remoteHeld && lockedSelected && !capturing;
    }
}

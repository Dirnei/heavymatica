package io.github.dirnei.mc.heavymatica.build;

import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import java.util.ArrayList;
import java.util.List;

/** Which build options the row above the hotbar shows, free of the game. */
public final class BuildOptionsRules {

    private BuildOptionsRules() {
    }

    /**
     * The options to show, Easy Place first: those that are on, while a locked placement is in the player's dimension,
     * the HUD is visible and no screen is open; otherwise none.
     */
    public static List<Entry> shown(boolean easyPlace, boolean restriction, boolean lockedInDimension, boolean screenOpen, boolean hideGui) {
        if (!lockedInDimension || screenOpen || hideGui) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>(2);
        if (easyPlace) {
            entries.add(Entry.EASY_PLACE);
        }
        if (restriction) {
            entries.add(Entry.RESTRICTION);
        }
        return List.copyOf(entries);
    }
}

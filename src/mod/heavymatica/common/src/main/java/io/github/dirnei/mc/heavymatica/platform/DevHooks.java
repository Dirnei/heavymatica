package io.github.dirnei.mc.heavymatica.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** The loader parts of the development aids. */
public interface DevHooks {

    /** The loader's mods list; null when there is none. */
    @Nullable Screen modsScreen(@Nullable Screen parent);

    /** Selects a mod in an open mods list, as a click on its row would. */
    void selectMod(Screen screen, String modId);

    /** Opens a mod's config screen from the open mods list, as its config button does. */
    void openModConfig(Screen modsScreen, String modId);

    /** Presses a key mapping once, as its key would. */
    void press(KeyMapping mapping);

    /** Holds or lets go of a key mapping, as its key would. */
    void hold(KeyMapping mapping, boolean down);

    /** Logs Heavymatica's key mappings that share a key with another mapping, with their contexts. */
    void logKeyConflicts(Minecraft minecraft);
}

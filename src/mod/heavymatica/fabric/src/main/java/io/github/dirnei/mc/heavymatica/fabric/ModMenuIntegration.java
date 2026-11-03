package io.github.dirnei.mc.heavymatica.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.dirnei.mc.heavymatica.ui.SettingsScreen;

/** Mod Menu's config button for Heavymatica. Only Mod Menu loads this class. */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}

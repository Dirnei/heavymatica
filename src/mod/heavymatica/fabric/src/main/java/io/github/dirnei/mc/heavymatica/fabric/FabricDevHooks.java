package io.github.dirnei.mc.heavymatica.fabric;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.fabric.mixin.KeyMappingAccessor;
import io.github.dirnei.mc.heavymatica.platform.DevHooks;
import io.github.dirnei.mc.heavymatica.platform.KeyContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

final class FabricDevHooks implements DevHooks {

    /** Mod Menu's mods list, when Mod Menu is installed. */
    @Override
    public @Nullable Screen modsScreen(@Nullable Screen parent) {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
            Heavymatica.LOGGER.warn("Dev mods: Mod Menu is not installed");
            return null;
        }
        try {
            return (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen").getConstructor(Screen.class).newInstance(parent);
        } catch (ReflectiveOperationException e) {
            Heavymatica.LOGGER.error("Dev mods: could not open Mod Menu", e);
            return null;
        }
    }

    /** Selects the mod's row in Mod Menu's list. */
    @Override
    public void selectMod(Screen screen, String modId) {
        try {
            for (Field field : screen.getClass().getDeclaredFields()) {
                if (!AbstractSelectionList.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                for (Object entry : ((AbstractSelectionList<?>) field.get(screen)).children()) {
                    Method getMod;
                    try {
                        getMod = entry.getClass().getMethod("getMod");
                    } catch (NoSuchMethodException notTheModList) {
                        break;
                    }
                    Object mod = getMod.invoke(entry);
                    if (modId.equals(mod.getClass().getMethod("getId").invoke(mod))) {
                        for (Method method : screen.getClass().getMethods()) {
                            if (method.getName().equals("updateSelectedEntry")) {
                                method.invoke(screen, entry);
                            }
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            Heavymatica.LOGGER.error("Dev selectMod failed", e);
        }
    }

    /** Mod Menu's config button for the mod. */
    @Override
    public void openModConfig(Screen modsScreen, String modId) {
        try {
            modsScreen.getClass().getMethod("safelyOpenConfigScreen", String.class).invoke(modsScreen, modId);
        } catch (ReflectiveOperationException e) {
            Heavymatica.LOGGER.error("Dev modConfig failed", e);
        }
    }

    /**
     * A bound mapping is pressed through vanilla's key handling. An unbound one is pressed on its own: vanilla would
     * press every unbound mapping at once.
     */
    @Override
    public void press(KeyMapping mapping) {
        if (mapping.isUnbound()) {
            KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
            accessor.heavymatica$setClickCount(accessor.heavymatica$clickCount() + 1);
        } else {
            KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mapping));
        }
    }

    @Override
    public void hold(KeyMapping mapping, boolean down) {
        if (mapping.isUnbound()) {
            mapping.setDown(down);
        } else {
            KeyMapping.set(KeyMappingHelper.getBoundKeyOf(mapping), down);
        }
    }

    @Override
    public void logKeyConflicts(Minecraft minecraft) {
        FabricKeys keys = FabricPlatform.keyRegistry();
        for (KeyMapping mine : minecraft.options.keyMappings) {
            if (!mine.getName().startsWith("key.heavymatica.") || mine.isUnbound()) {
                continue;
            }
            for (KeyMapping other : minecraft.options.keyMappings) {
                if (other != mine && mine.same(other)) {
                    KeyContext mineContext = keys.context(mine);
                    KeyContext otherContext = keys.context(other);
                    Heavymatica.LOGGER.info("Dev conflict: {} ({}, {}) with {} ({}, {}): contexts {} / {}", mine.getName(),
                            KeyMappingHelper.getBoundKeyOf(mine).getName(), mineContext, other.getName(), KeyMappingHelper.getBoundKeyOf(other).getName(),
                            otherContext, mineContext != null && otherContext != null && mineContext.conflicts(otherContext),
                            mineContext != null && otherContext != null && otherContext.conflicts(mineContext));
                }
            }
        }
    }
}

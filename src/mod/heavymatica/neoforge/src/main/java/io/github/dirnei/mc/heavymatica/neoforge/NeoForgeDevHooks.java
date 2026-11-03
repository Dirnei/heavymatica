package io.github.dirnei.mc.heavymatica.neoforge;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.platform.DevHooks;
import java.lang.reflect.Field;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ModListScreen;
import org.jetbrains.annotations.Nullable;

final class NeoForgeDevHooks implements DevHooks {

    @Override
    public @Nullable Screen modsScreen(@Nullable Screen parent) {
        return new ModListScreen(parent);
    }

    @Override
    public void selectMod(Screen screen, String modId) {
        if (!(screen instanceof ModListScreen)) {
            return;
        }
        try {
            for (Field field : screen.getClass().getDeclaredFields()) {
                if (field.getType().getSimpleName().equals("ModListWidget")) {
                    field.setAccessible(true);
                    AbstractSelectionList<?> list = (AbstractSelectionList<?>) field.get(screen);
                    for (Object entry : list.children()) {
                        Object info = entry.getClass().getMethod("getInfo").invoke(entry);
                        if (modId.equals(info.getClass().getMethod("getModId").invoke(info))) {
                            screen.getClass().getMethod("setSelected", entry.getClass()).invoke(screen, entry);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            Heavymatica.LOGGER.error("Dev selectMod failed", e);
        }
    }

    @Override
    public void openModConfig(Screen modsScreen, String modId) {
        ModList.get().getModContainerById(modId)
                .flatMap(container -> container.getCustomExtension(IConfigScreenFactory.class)
                        .map(factory -> factory.createScreen(container, modsScreen)))
                .ifPresentOrElse(screen -> Minecraft.getInstance().setScreen(screen),
                        () -> Heavymatica.LOGGER.warn("Dev modConfig: {} has no config screen", modId));
    }

    @Override
    public void press(KeyMapping mapping) {
        KeyMapping.click(mapping.getKey());
    }

    @Override
    public void hold(KeyMapping mapping, boolean down) {
        KeyMapping.set(mapping.getKey(), down);
    }

    @Override
    public void logKeyConflicts(Minecraft minecraft) {
        for (KeyMapping mine : minecraft.options.keyMappings) {
            if (!mine.getName().startsWith("key.heavymatica.")) {
                continue;
            }
            for (KeyMapping other : minecraft.options.keyMappings) {
                if (other != mine && mine.same(other) || other.hasKeyModifierConflict(mine)) {
                    Heavymatica.LOGGER.info("Dev conflict: {} ({}, {}) with {} ({}, {}): same {}, modifier {}, contexts {} / {}, modifiers {} / {}",
                            mine.getName(), mine.getKey().getName(), mine.getKeyConflictContext(), other.getName(), other.getKey().getName(),
                            other.getKeyConflictContext(), mine.same(other), other.hasKeyModifierConflict(mine),
                            mine.getKeyConflictContext().conflicts(other.getKeyConflictContext()),
                            other.getKeyConflictContext().conflicts(mine.getKeyConflictContext()), mine.getKeyModifier(), other.getKeyModifier());
                }
            }
        }
    }
}

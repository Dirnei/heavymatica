package io.github.dirnei.mc.heavymatica.build;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.remote.RemoteKeys;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** The Easy Place and placement restriction toggles: no default key, active whenever no screen is open. */
public final class BuildKeys {

    public static final IKeyConflictContext NO_SCREEN = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen == null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.IN_GAME || other == RemoteKeys.HOLDING_REMOTE
                    || other == RemoteKeys.LAYER_KEYS;
        }
    };

    public static final KeyMapping TOGGLE_EASY_PLACE = key("toggle_easy_place");
    public static final KeyMapping TOGGLE_RESTRICTION = key("toggle_restriction");

    public static final List<KeyMapping> ALL = List.of(TOGGLE_EASY_PLACE, TOGGLE_RESTRICTION);

    private BuildKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        ALL.forEach(event::register);
    }

    private static KeyMapping key(String name) {
        return new KeyMapping("key.heavymatica." + name, NO_SCREEN, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, HeavymaticaClient.CATEGORY);
    }
}

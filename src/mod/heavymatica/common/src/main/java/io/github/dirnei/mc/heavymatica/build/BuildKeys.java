package io.github.dirnei.mc.heavymatica.build;

import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.platform.KeyContext;
import io.github.dirnei.mc.heavymatica.platform.KeyRegistry;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import io.github.dirnei.mc.heavymatica.remote.RemoteKeys;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** The Easy Place and placement restriction toggles: no default key, active whenever no screen is open. */
public final class BuildKeys {

    public static final KeyContext NO_SCREEN = new KeyContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen == null;
        }

        @Override
        public boolean conflicts(KeyContext other) {
            return other == this || other == KeyContext.IN_GAME || other == RemoteKeys.HOLDING_REMOTE
                    || other == RemoteKeys.LAYER_KEYS || other == RemoteKeys.WITH_SELECTION;
        }

        @Override
        public String toString() {
            return "NO_SCREEN";
        }
    };

    public static final KeyMapping TOGGLE_EASY_PLACE = key("toggle_easy_place");
    public static final KeyMapping TOGGLE_RESTRICTION = key("toggle_restriction");

    public static final List<KeyMapping> ALL = List.of(TOGGLE_EASY_PLACE, TOGGLE_RESTRICTION);

    private BuildKeys() {
    }

    public static void register(KeyRegistry keys) {
        ALL.forEach(keys::register);
    }

    private static KeyMapping key(String name) {
        return Platform.get().keys().create("key.heavymatica." + name, NO_SCREEN, GLFW.GLFW_KEY_UNKNOWN, HeavymaticaClient.CATEGORY);
    }
}

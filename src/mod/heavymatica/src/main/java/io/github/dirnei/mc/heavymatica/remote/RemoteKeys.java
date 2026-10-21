package io.github.dirnei.mc.heavymatica.remote;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The placement keys. They live in {@link #HOLDING_REMOTE}, so they only act while a remote is in the main hand
 * and no screen is open. Shift is read when a key is pressed rather than bound, so rebinding keeps the Shift variants.
 */
public final class RemoteKeys {

    public static final IKeyConflictContext HOLDING_REMOTE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen == null && Remotes.held();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping MOVE_FORWARD = key("move_forward", GLFW.GLFW_KEY_UP);
    public static final KeyMapping MOVE_BACK = key("move_back", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping MOVE_LEFT = key("move_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping MOVE_RIGHT = key("move_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping MOVE_UP = key("move_up", GLFW.GLFW_KEY_KP_ADD);
    public static final KeyMapping MOVE_DOWN = key("move_down", GLFW.GLFW_KEY_KP_SUBTRACT);
    public static final KeyMapping ROTATE = key("rotate", GLFW.GLFW_KEY_R);
    public static final KeyMapping MIRROR = key("mirror", GLFW.GLFW_KEY_M);
    public static final KeyMapping REMOVE = key("remove", GLFW.GLFW_KEY_DELETE);

    public static final List<KeyMapping> ALL = List.of(MOVE_FORWARD, MOVE_BACK, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN, ROTATE, MIRROR, REMOVE);

    private RemoteKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        ALL.forEach(event::register);
    }

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.heavymatica." + name, HOLDING_REMOTE, InputConstants.Type.KEYSYM, key, HeavymaticaClient.CATEGORY);
    }
}

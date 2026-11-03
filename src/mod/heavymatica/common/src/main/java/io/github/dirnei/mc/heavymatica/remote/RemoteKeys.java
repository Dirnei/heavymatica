package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.platform.KeyContext;
import io.github.dirnei.mc.heavymatica.platform.KeyRegistry;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The placement keys. Most live in {@link #HOLDING_REMOTE}, so they only act while a remote is in the main hand and no
 * screen is open; the layer keys live in {@link #LAYER_KEYS}, which also acts without the remote while the selected
 * placement is locked, and the wheel and its action keys in {@link #WITH_SELECTION}, which also acts without the remote
 * while a placement is selected outside capture mode. Shift is read when a key is pressed rather than bound, so rebinding keeps the Shift variants.
 * The actions on the wheel have no default key.
 */
public final class RemoteKeys {

    public static final KeyContext HOLDING_REMOTE = new KeyContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen == null && Remotes.held();
        }

        @Override
        public boolean conflicts(KeyContext other) {
            return other == this || other == LAYER_KEYS || other == WITH_SELECTION;
        }
    };

    private static BooleanSupplier lockedSelected = () -> false;
    private static BooleanSupplier selected = () -> false;
    private static BooleanSupplier capturing = () -> false;

    public static final KeyContext LAYER_KEYS = new KeyContext() {
        @Override
        public boolean isActive() {
            return LayerKeyRules.active(Minecraft.getInstance().screen != null, Remotes.held(), lockedSelected.getAsBoolean());
        }

        @Override
        public boolean conflicts(KeyContext other) {
            return other == this || other == HOLDING_REMOTE || other == WITH_SELECTION;
        }
    };

    public static final KeyContext WITH_SELECTION = new KeyContext() {
        @Override
        public boolean isActive() {
            return LayerKeyRules.wheelActive(Minecraft.getInstance().screen != null, Remotes.held(), selected.getAsBoolean(), capturing.getAsBoolean());
        }

        @Override
        public boolean conflicts(KeyContext other) {
            return other == this || other == HOLDING_REMOTE || other == LAYER_KEYS;
        }
    };

    public static final KeyMapping MOVE_FORWARD = key("move_forward", LAYER_KEYS, GLFW.GLFW_KEY_UP);
    public static final KeyMapping MOVE_BACK = key("move_back", LAYER_KEYS, GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping MOVE_LEFT = key("move_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping MOVE_RIGHT = key("move_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping MOVE_UP = key("move_up", LAYER_KEYS, GLFW.GLFW_KEY_KP_ADD);
    public static final KeyMapping MOVE_DOWN = key("move_down", LAYER_KEYS, GLFW.GLFW_KEY_KP_SUBTRACT);
    public static final KeyMapping ROTATE = key("rotate", GLFW.GLFW_KEY_R);
    public static final KeyMapping MIRROR = key("mirror", GLFW.GLFW_KEY_M);
    public static final KeyMapping WHEEL = key("wheel", WITH_SELECTION, GLFW.GLFW_KEY_V);
    public static final KeyMapping REMOVE = key("remove", WITH_SELECTION, GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping LOCK = key("lock", WITH_SELECTION, GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping IN_HAND = key("in_hand", WITH_SELECTION, GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping MATERIALS = key("materials", WITH_SELECTION, GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping CYCLE_LAYERS = key("cycle_layers", LAYER_KEYS, GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping SWAP_CORNER = key("swap_corner", GLFW.GLFW_KEY_UNKNOWN);

    public static final List<KeyMapping> ALL = List.of(MOVE_FORWARD, MOVE_BACK, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN, ROTATE, MIRROR, WHEEL, REMOVE,
            LOCK, IN_HAND, MATERIALS, CYCLE_LAYERS, SWAP_CORNER);

    /** The keys that also act without the remote while the selected placement is locked. */
    public static final List<KeyMapping> LAYER = List.of(MOVE_FORWARD, MOVE_BACK, MOVE_UP, MOVE_DOWN, CYCLE_LAYERS);

    private RemoteKeys() {
    }

    public static void register(KeyRegistry keys) {
        ALL.forEach(keys::register);
    }

    /** The wheel key and the wheel action keys, which also act without the remote while a placement is selected. */
    public static final List<KeyMapping> WITH_SELECTED = List.of(WHEEL, REMOVE, LOCK, IN_HAND, MATERIALS);

    /** Tells the wheel keys whether a placement is selected and whether capture mode is on. */
    public static void selection(BooleanSupplier selectedNow, BooleanSupplier capturingNow) {
        selected = selectedNow;
        capturing = capturingNow;
    }

    /** Tells the layer keys whether the selected placement is locked. */
    public static void lockedSelected(BooleanSupplier locked) {
        lockedSelected = locked;
    }

    private static KeyMapping key(String name, int key) {
        return key(name, HOLDING_REMOTE, key);
    }

    private static KeyMapping key(String name, KeyContext context, int key) {
        return Platform.get().keys().create("key.heavymatica." + name, context, key, HeavymaticaClient.CATEGORY);
    }
}

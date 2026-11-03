package io.github.dirnei.mc.heavymatica.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Key mappings with a {@link KeyContext}. Create them any time; register them while the client is constructed. */
public interface KeyRegistry {

    /** A key mapping on a keyboard key: a GLFW key code, or GLFW_KEY_UNKNOWN for none. */
    KeyMapping create(String name, KeyContext context, int keyCode, KeyMapping.Category category);

    void registerCategory(KeyMapping.Category category);

    void register(KeyMapping mapping);

    /** The key the player bound the mapping to. */
    InputConstants.Key boundKey(KeyMapping mapping);
}

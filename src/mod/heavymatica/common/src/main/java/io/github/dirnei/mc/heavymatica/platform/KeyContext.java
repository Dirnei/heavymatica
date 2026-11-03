package io.github.dirnei.mc.heavymatica.platform;

import net.minecraft.client.Minecraft;

/**
 * When a key mapping acts. Mappings whose context is inactive ignore their key, so mappings whose contexts don't
 * conflict can share a key.
 */
public interface KeyContext {

    /** Active while no screen is open. */
    KeyContext IN_GAME = new KeyContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen == null;
        }

        @Override
        public boolean conflicts(KeyContext other) {
            return other == this;
        }

        @Override
        public String toString() {
            return "IN_GAME";
        }
    };

    boolean isActive();

    /** Whether both contexts can be active at once, so mappings in them should not share a key. */
    boolean conflicts(KeyContext other);
}

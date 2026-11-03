package io.github.dirnei.mc.heavymatica.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.platform.KeyContext;
import io.github.dirnei.mc.heavymatica.platform.KeyRegistry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Key mappings with NeoForge's conflict contexts, one per {@link KeyContext}. */
final class NeoForgeKeys implements KeyRegistry {

    private final Map<KeyContext, IKeyConflictContext> contexts = new IdentityHashMap<>();
    private final List<KeyMapping.Category> categories = new ArrayList<>();
    private final List<KeyMapping> mappings = new ArrayList<>();

    @Override
    public KeyMapping create(String name, KeyContext context, int keyCode, KeyMapping.Category category) {
        return new KeyMapping(name, conflictContext(context), InputConstants.Type.KEYSYM, keyCode, category);
    }

    @Override
    public void registerCategory(KeyMapping.Category category) {
        categories.add(category);
    }

    @Override
    public void register(KeyMapping mapping) {
        mappings.add(mapping);
    }

    @Override
    public InputConstants.Key boundKey(KeyMapping mapping) {
        return mapping.getKey();
    }

    void registerAll(RegisterKeyMappingsEvent event) {
        categories.forEach(event::registerCategory);
        mappings.forEach(event::register);
    }

    private synchronized IKeyConflictContext conflictContext(KeyContext context) {
        if (context == KeyContext.IN_GAME) {
            return KeyConflictContext.IN_GAME;
        }
        return contexts.computeIfAbsent(context, Adapter::new);
    }

    private static KeyContext keyContext(IKeyConflictContext context) {
        if (context == KeyConflictContext.IN_GAME) {
            return KeyContext.IN_GAME;
        }
        return context instanceof Adapter adapter ? adapter.context : null;
    }

    private record Adapter(KeyContext context) implements IKeyConflictContext {

        @Override
        public boolean isActive() {
            return context.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            KeyContext mapped = keyContext(other);
            return mapped != null && context.conflicts(mapped);
        }

        @Override
        public String toString() {
            return context.toString();
        }
    }
}

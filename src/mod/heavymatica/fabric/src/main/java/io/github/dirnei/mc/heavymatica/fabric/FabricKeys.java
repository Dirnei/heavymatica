package io.github.dirnei.mc.heavymatica.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.platform.KeyContext;
import io.github.dirnei.mc.heavymatica.platform.KeyRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.jetbrains.annotations.Nullable;

/**
 * Key mappings with a {@link KeyContext}. Vanilla lets several mappings share a key and sends every press to all of
 * them; the key mapping mixin asks {@link #active} so a mapping whose context is inactive ignores its key, as with
 * NeoForge's conflict contexts.
 */
public final class FabricKeys implements KeyRegistry {

    private final Map<KeyMapping, KeyContext> contexts = new ConcurrentHashMap<>();

    @Override
    public KeyMapping create(String name, KeyContext context, int keyCode, KeyMapping.Category category) {
        KeyMapping mapping = new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, category);
        contexts.put(mapping, context);
        return mapping;
    }

    @Override
    public void registerCategory(KeyMapping.Category category) {
        KeyMapping.Category.register(category.id());
    }

    @Override
    public void register(KeyMapping mapping) {
        KeyMappingHelper.registerKeyMapping(mapping);
    }

    @Override
    public InputConstants.Key boundKey(KeyMapping mapping) {
        return KeyMappingHelper.getBoundKeyOf(mapping);
    }

    /** False for a Heavymatica mapping whose context is inactive; true for every other mapping. */
    public boolean active(KeyMapping mapping) {
        KeyContext context = contexts.get(mapping);
        return context == null || context.isActive();
    }

    /** The context of a Heavymatica mapping; null for other mappings. */
    public @Nullable KeyContext context(KeyMapping mapping) {
        return contexts.get(mapping);
    }
}

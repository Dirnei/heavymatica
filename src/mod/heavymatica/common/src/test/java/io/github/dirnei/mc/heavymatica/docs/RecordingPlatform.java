package io.github.dirnei.mc.heavymatica.docs;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.platform.ClientEvents;
import io.github.dirnei.mc.heavymatica.platform.DevHooks;
import io.github.dirnei.mc.heavymatica.platform.HudLayers;
import io.github.dirnei.mc.heavymatica.platform.ItemContainers;
import io.github.dirnei.mc.heavymatica.platform.KeyContext;
import io.github.dirnei.mc.heavymatica.platform.KeyRegistry;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import io.github.dirnei.mc.heavymatica.platform.ServerEvents;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;

/** The platform of the common tests: it creates plain key mappings and remembers their context, nothing else. */
public final class RecordingPlatform implements Platform {

    static final Map<KeyMapping, KeyContext> CONTEXTS = new IdentityHashMap<>();

    private static final KeyRegistry KEYS = new Registered();

    /** Creates plain key mappings and records the ones registered with it, in order. */
    static final class Registered implements KeyRegistry {

        final List<KeyMapping> mappings = new ArrayList<>();

        @Override
        public KeyMapping create(String name, KeyContext context, int keyCode, KeyMapping.Category category) {
            KeyMapping mapping = new KeyMapping(name, InputConstants.Type.KEYSYM, keyCode, category);
            CONTEXTS.put(mapping, context);
            return mapping;
        }

        @Override
        public void registerCategory(KeyMapping.Category category) {
        }

        @Override
        public void register(KeyMapping mapping) {
            mappings.add(mapping);
        }

        @Override
        public InputConstants.Key boundKey(KeyMapping mapping) {
            return mapping.getDefaultKey();
        }
    }

    @Override
    public String loaderName() {
        return "Test";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return false;
    }

    @Override
    public Path configDirectory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClientEvents clientEvents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServerEvents serverEvents() {
        throw new UnsupportedOperationException();
    }

    @Override
    public HudLayers hudLayers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public KeyRegistry keys() {
        return KEYS;
    }

    @Override
    public ItemContainers itemContainers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerPipeline(RenderPipeline pipeline) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DevHooks dev() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Block emptyFlowerPot(FlowerPotBlock pottedPlant) {
        throw new UnsupportedOperationException();
    }
}

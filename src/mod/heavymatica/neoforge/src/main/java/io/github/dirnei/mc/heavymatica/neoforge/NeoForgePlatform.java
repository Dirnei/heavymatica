package io.github.dirnei.mc.heavymatica.neoforge;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.dirnei.mc.heavymatica.platform.ClientEvents;
import io.github.dirnei.mc.heavymatica.platform.DevHooks;
import io.github.dirnei.mc.heavymatica.platform.HudLayers;
import io.github.dirnei.mc.heavymatica.platform.ItemContainers;
import io.github.dirnei.mc.heavymatica.platform.KeyRegistry;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import io.github.dirnei.mc.heavymatica.platform.ServerEvents;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * NeoForge's side of {@link Platform}. Registrations that NeoForge only takes during its registration events are kept
 * until the client entry point hands those events over.
 */
public final class NeoForgePlatform implements Platform {

    private static final NeoForgeClientEvents CLIENT_EVENTS = new NeoForgeClientEvents();
    private static final NeoForgeServerEvents SERVER_EVENTS = new NeoForgeServerEvents();
    private static final NeoForgeHudLayers HUD_LAYERS = new NeoForgeHudLayers();
    private static final NeoForgeKeys KEYS = new NeoForgeKeys();
    private static final NeoForgeItemContainers ITEM_CONTAINERS = new NeoForgeItemContainers();
    private static final NeoForgeDevHooks DEV = new NeoForgeDevHooks();
    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public ClientEvents clientEvents() {
        return CLIENT_EVENTS;
    }

    @Override
    public ServerEvents serverEvents() {
        return SERVER_EVENTS;
    }

    @Override
    public HudLayers hudLayers() {
        return HUD_LAYERS;
    }

    @Override
    public KeyRegistry keys() {
        return KEYS;
    }

    @Override
    public ItemContainers itemContainers() {
        return ITEM_CONTAINERS;
    }

    @Override
    public void registerPipeline(RenderPipeline pipeline) {
        PIPELINES.add(pipeline);
    }

    @Override
    public DevHooks dev() {
        return DEV;
    }

    @Override
    public Block emptyFlowerPot(FlowerPotBlock pottedPlant) {
        return pottedPlant.getEmptyPot();
    }

    static NeoForgeHudLayers hud() {
        return HUD_LAYERS;
    }

    static NeoForgeKeys keyRegistry() {
        return KEYS;
    }

    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        PIPELINES.forEach(event::registerPipeline);
    }
}

package io.github.dirnei.mc.heavymatica.fabric;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import io.github.dirnei.mc.heavymatica.platform.ClientEvents;
import io.github.dirnei.mc.heavymatica.platform.DevHooks;
import io.github.dirnei.mc.heavymatica.platform.HudLayers;
import io.github.dirnei.mc.heavymatica.platform.ItemContainers;
import io.github.dirnei.mc.heavymatica.platform.KeyRegistry;
import io.github.dirnei.mc.heavymatica.platform.Platform;
import io.github.dirnei.mc.heavymatica.platform.ServerEvents;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;

/** Fabric's side of {@link Platform}: Fabric API events, plus the mixins in this jar where Fabric API has none. */
public final class FabricPlatform implements Platform {

    private static final FabricClientEvents CLIENT_EVENTS = new FabricClientEvents();
    private static final FabricServerEvents SERVER_EVENTS = new FabricServerEvents();
    private static final FabricHudLayers HUD_LAYERS = new FabricHudLayers();
    private static final FabricKeys KEYS = new FabricKeys();
    private static final FabricItemContainers ITEM_CONTAINERS = new FabricItemContainers();
    private static final FabricDevHooks DEV = new FabricDevHooks();

    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
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
        RenderPipelines.register(pipeline);
    }

    @Override
    public DevHooks dev() {
        return DEV;
    }

    @Override
    public Block emptyFlowerPot(FlowerPotBlock pottedPlant) {
        return Blocks.FLOWER_POT;
    }

    /** The events the mixins report. */
    public static FabricClientEvents events() {
        return CLIENT_EVENTS;
    }

    public static FabricKeys keyRegistry() {
        return KEYS;
    }
}

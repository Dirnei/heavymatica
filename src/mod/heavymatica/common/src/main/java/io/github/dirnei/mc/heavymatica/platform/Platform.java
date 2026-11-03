package io.github.dirnei.mc.heavymatica.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.nio.file.Path;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;

/** What the mod loader provides to Heavymatica. Each loader project implements it and lists it in META-INF/services. */
public interface Platform {

    static Platform get() {
        return Holder.INSTANCE;
    }

    /** The loader's name as players know it, for example NeoForge or Fabric. */
    String loaderName();

    boolean isModLoaded(String modId);

    Path configDirectory();

    ClientEvents clientEvents();

    ServerEvents serverEvents();

    HudLayers hudLayers();

    KeyRegistry keys();

    ItemContainers itemContainers();

    /** Registers a render pipeline so the game compiles it with the built-in ones. */
    void registerPipeline(RenderPipeline pipeline);

    DevHooks dev();

    /** The empty pot a potted plant comes out of; loaders can have flower pots other than the vanilla one. */
    Block emptyFlowerPot(FlowerPotBlock pottedPlant);

    final class Holder {

        private static final Platform INSTANCE = Services.load(Platform.class);

        private Holder() {
        }
    }
}

package io.github.dirnei.mc.heavymatica.neoforge;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.ui.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Heavymatica.MOD_ID, dist = Dist.CLIENT)
public final class HeavymaticaNeoForgeClient {

    public HeavymaticaNeoForgeClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, NeoForgeSettings.bind(HeavymaticaConfig.SCHEMA));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        HeavymaticaClient.construct(container.getModInfo().getVersion().toString());
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> NeoForgePlatform.keyRegistry().registerAll(event));
        modEventBus.addListener((RegisterGuiLayersEvent event) -> NeoForgePlatform.hud().registerAll(event));
        modEventBus.addListener((RegisterRenderPipelinesEvent event) -> NeoForgePlatform.registerPipelines(event));
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(HeavymaticaClient::setup));
    }
}

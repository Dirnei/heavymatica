package io.github.dirnei.mc.heavymatica.fabric;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.settings.TomlSettingsFile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class HeavymaticaFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Heavymatica.LOGGER.info("Heavymatica loaded");
        FabricLoader loader = FabricLoader.getInstance();
        TomlSettingsFile.bind(HeavymaticaConfig.SCHEMA, loader.getConfigDir().resolve("heavymatica-client.toml"));
        String version = loader.getModContainer(Heavymatica.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("0");
        HeavymaticaClient.construct(version);
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            private boolean started;

            @Override
            public void onEndTick(Minecraft client) {
                // The services need the block models, which the first resource load (under the loading overlay) builds.
                if (!started && client.getOverlay() == null) {
                    started = true;
                    HeavymaticaClient.setup();
                }
            }
        });
    }
}

package io.github.dirnei.mc.heavymatica.neoforge;

import io.github.dirnei.mc.heavymatica.platform.ServerEvents;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class NeoForgeServerEvents implements ServerEvents {

    @Override
    public void onTickEnd(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onStopping(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> listener.run());
    }
}

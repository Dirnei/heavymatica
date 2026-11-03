package io.github.dirnei.mc.heavymatica.fabric;

import io.github.dirnei.mc.heavymatica.platform.ServerEvents;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

final class FabricServerEvents implements ServerEvents {

    @Override
    public void onTickEnd(Consumer<MinecraftServer> listener) {
        ServerTickEvents.END_SERVER_TICK.register(listener::accept);
    }

    @Override
    public void onStopping(Runnable listener) {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> listener.run());
    }
}

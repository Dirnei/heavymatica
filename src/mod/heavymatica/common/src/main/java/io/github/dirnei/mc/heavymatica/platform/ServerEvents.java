package io.github.dirnei.mc.heavymatica.platform;

import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Server events, also of the singleplayer server. Listeners run on the server thread. */
public interface ServerEvents {

    void onTickEnd(Consumer<MinecraftServer> listener);

    void onStopping(Runnable listener);
}

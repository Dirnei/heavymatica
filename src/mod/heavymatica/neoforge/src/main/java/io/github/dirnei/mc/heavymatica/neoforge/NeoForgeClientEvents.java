package io.github.dirnei.mc.heavymatica.neoforge;

import io.github.dirnei.mc.heavymatica.platform.ClientEvents;
import java.util.function.Consumer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4fc;

final class NeoForgeClientEvents implements ClientEvents {

    @Override
    public void onTickStart(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> listener.run());
    }

    @Override
    public void onTickEnd(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> listener.run());
    }

    @Override
    public void onFrameStart(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> listener.run());
    }

    @Override
    public void onLogin(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> listener.run());
    }

    @Override
    public void onLogout(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> listener.run());
    }

    @Override
    public void afterTranslucentBlocks(LevelStageListener listener) {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks event) ->
                listener.render(event.getPoseStack(), event.getModelViewMatrix()));
    }

    @Override
    public void afterLevel(Consumer<Matrix4fc> listener) {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterLevel event) -> listener.accept(event.getModelViewMatrix()));
    }

    @Override
    public void onUseItem(UseListener listener) {
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            if (!event.isUseItem()) {
                return;
            }
            switch (listener.use(event.getHand())) {
                case PASS -> {
                }
                case CANCEL -> {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
                case CANCEL_AND_SWING -> {
                    event.setCanceled(true);
                    event.setSwingHand(true);
                }
            }
        });
    }

    @Override
    public void onScroll(ScrollListener listener) {
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event) -> {
            if (listener.scroll(event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
        });
    }
}

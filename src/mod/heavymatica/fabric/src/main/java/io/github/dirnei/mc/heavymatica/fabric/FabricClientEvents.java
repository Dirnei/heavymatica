package io.github.dirnei.mc.heavymatica.fabric;

import io.github.dirnei.mc.heavymatica.platform.ClientEvents;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.InteractionHand;
import org.joml.Matrix4fc;

/** Client events through Fabric API, and through this jar's mixins where Fabric API has no matching event. */
public final class FabricClientEvents implements ClientEvents {

    private final List<Runnable> frameStart = new CopyOnWriteArrayList<>();
    private final List<Consumer<Matrix4fc>> afterLevel = new CopyOnWriteArrayList<>();
    private final List<UseListener> useItem = new CopyOnWriteArrayList<>();
    private final List<ScrollListener> scroll = new CopyOnWriteArrayList<>();

    @Override
    public void onTickStart(Runnable listener) {
        ClientTickEvents.START_CLIENT_TICK.register(client -> listener.run());
    }

    @Override
    public void onTickEnd(Runnable listener) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> listener.run());
    }

    @Override
    public void onFrameStart(Runnable listener) {
        frameStart.add(listener);
    }

    @Override
    public void onLogin(Runnable listener) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(listener));
    }

    @Override
    public void onLogout(Runnable listener) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(listener));
    }

    @Override
    public void afterTranslucentBlocks(LevelStageListener listener) {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context ->
                listener.render(context.poseStack(), context.levelState().cameraRenderState.viewRotationMatrix));
    }

    @Override
    public void afterLevel(Consumer<Matrix4fc> listener) {
        afterLevel.add(listener);
    }

    @Override
    public void onUseItem(UseListener listener) {
        useItem.add(listener);
    }

    @Override
    public void onScroll(ScrollListener listener) {
        scroll.add(listener);
    }

    public void frameStarted() {
        frameStart.forEach(Runnable::run);
    }

    public void levelRendered(Matrix4fc modelView) {
        afterLevel.forEach(listener -> listener.accept(modelView));
    }

    /** The first listener that cancels decides; later listeners don't see the use. */
    public UseResult useItem(InteractionHand hand) {
        for (UseListener listener : useItem) {
            UseResult result = listener.use(hand);
            if (result != UseResult.PASS) {
                return result;
            }
        }
        return UseResult.PASS;
    }

    /** True when a listener cancels the scroll. */
    public boolean scrolled(double deltaY) {
        for (ScrollListener listener : scroll) {
            if (listener.scroll(deltaY)) {
                return true;
            }
        }
        return false;
    }
}

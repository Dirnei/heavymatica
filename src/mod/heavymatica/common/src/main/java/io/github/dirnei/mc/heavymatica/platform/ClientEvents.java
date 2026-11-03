package io.github.dirnei.mc.heavymatica.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import org.joml.Matrix4fc;

/** Client events. Listeners run on the game thread, in the order they were added. */
public interface ClientEvents {

    void onTickStart(Runnable listener);

    void onTickEnd(Runnable listener);

    /** Before the game renders a frame. */
    void onFrameStart(Runnable listener);

    /** After the client joined a world or server. */
    void onLogin(Runnable listener);

    /** When the client leaves a world or server. */
    void onLogout(Runnable listener);

    /** After the level's translucent terrain is drawn; gets the level's pose stack and the view's model-view matrix. */
    void afterTranslucentBlocks(LevelStageListener listener);

    /** After the whole level is drawn, before the hand; gets the view's model-view matrix. */
    void afterLevel(Consumer<Matrix4fc> listener);

    /**
     * When the use key is about to act with a hand. A listener that cancels stops the use for this click, and later
     * listeners don't see it.
     */
    void onUseItem(UseListener listener);

    /** Mouse scrolling in the world without a screen. A listener that cancels keeps the hotbar from scrolling. */
    void onScroll(ScrollListener listener);

    enum UseResult {
        PASS,
        CANCEL,
        CANCEL_AND_SWING,
    }

    @FunctionalInterface
    interface LevelStageListener {
        void render(PoseStack poseStack, Matrix4fc modelView);
    }

    @FunctionalInterface
    interface UseListener {
        UseResult use(InteractionHand hand);
    }

    @FunctionalInterface
    interface ScrollListener {
        /** True cancels the scroll. */
        boolean scroll(double deltaY);
    }
}

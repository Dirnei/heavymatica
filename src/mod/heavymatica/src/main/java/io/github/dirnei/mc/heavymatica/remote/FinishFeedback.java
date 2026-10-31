package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.placement.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** The sound and toast for a finished placement. */
public final class FinishFeedback {

    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(10_000L);

    private FinishFeedback() {
    }

    public static void finished(Placement placement) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f));
        Component title = Component.translatable("heavymatica.finish.title", placement.name());
        Component hint = Component.translatable("heavymatica.finish.hint", RemoteKeys.WHEEL.getTranslatedKeyMessage());
        minecraft.getToastManager().addToast(SystemToast.multiline(minecraft, TOAST, title, hint));
    }
}

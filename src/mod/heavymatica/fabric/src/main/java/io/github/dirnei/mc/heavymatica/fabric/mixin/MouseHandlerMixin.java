package io.github.dirnei.mc.heavymatica.fabric.mixin;

import io.github.dirnei.mc.heavymatica.fabric.FabricPlatform;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling in the world without a screen, before the hotbar or spectator menu reacts, as NeoForge's scroll event. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    @Inject(method = "onScroll", cancellable = true, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
    private void heavymatica$scroll(long handle, double xoffset, double yoffset, CallbackInfo callback) {
        if (FabricPlatform.events().scrolled(yoffset)) {
            callback.cancel();
        }
    }
}

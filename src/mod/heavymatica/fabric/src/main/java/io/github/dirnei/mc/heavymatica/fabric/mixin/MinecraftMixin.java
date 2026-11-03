package io.github.dirnei.mc.heavymatica.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.dirnei.mc.heavymatica.fabric.FabricPlatform;
import io.github.dirnei.mc.heavymatica.platform.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The use key per hand, at the point where NeoForge posts its interaction event. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {

    @Shadow
    public @Nullable LocalPlayer player;

    @Inject(method = "startUseItem", cancellable = true, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"))
    private void heavymatica$useItem(CallbackInfo callback, @Local InteractionHand hand) {
        ClientEvents.UseResult result = FabricPlatform.events().useItem(hand);
        if (result == ClientEvents.UseResult.PASS) {
            return;
        }
        if (result == ClientEvents.UseResult.CANCEL_AND_SWING && player != null) {
            player.swing(hand);
        }
        callback.cancel();
    }
}

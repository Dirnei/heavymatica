package io.github.dirnei.mc.heavymatica.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.dirnei.mc.heavymatica.fabric.FabricPlatform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The start of a frame, and right after the level is drawn and before the hand, where NeoForge posts those events. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void heavymatica$frameStart(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callback) {
        FabricPlatform.events().frameStarted();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V"))
    private void heavymatica$afterLevel(DeltaTracker deltaTracker, CallbackInfo callback, @Local Matrix4fc modelViewMatrix) {
        FabricPlatform.events().levelRendered(modelViewMatrix);
    }
}

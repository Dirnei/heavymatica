package io.github.dirnei.mc.heavymatica.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.dirnei.mc.heavymatica.fabric.FabricPlatform;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Presses of a key reach only the mappings whose context is active, so Heavymatica's mappings can share keys with
 * others. Releases always reach every mapping, so no mapping stays held after its context ends.
 */
@Mixin(KeyMapping.class)
abstract class KeyMappingMixin {

    @Unique
    private static boolean heavymatica$releasing;

    @Inject(method = "set", at = @At("HEAD"))
    private static void heavymatica$setStart(InputConstants.Key key, boolean state, CallbackInfo callback) {
        heavymatica$releasing = !state;
    }

    @Inject(method = "set", at = @At("RETURN"))
    private static void heavymatica$setEnd(InputConstants.Key key, boolean state, CallbackInfo callback) {
        heavymatica$releasing = false;
    }

    @WrapOperation(method = "forAllKeyMappings", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private static void heavymatica$onlyActive(Consumer<Object> operation, Object mapping, Operation<Void> original) {
        if (heavymatica$releasing || FabricPlatform.keyRegistry().active((KeyMapping) mapping)) {
            original.call(operation, mapping);
        }
    }
}

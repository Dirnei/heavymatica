package io.github.dirnei.mc.heavymatica.fabric.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Development aid: presses a single key mapping. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount")
    int heavymatica$clickCount();

    @Accessor("clickCount")
    void heavymatica$setClickCount(int clickCount);
}

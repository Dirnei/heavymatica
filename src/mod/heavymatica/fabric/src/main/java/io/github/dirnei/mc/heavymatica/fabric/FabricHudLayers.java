package io.github.dirnei.mc.heavymatica.fabric;

import io.github.dirnei.mc.heavymatica.platform.HudLayers;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

final class FabricHudLayers implements HudLayers {

    @Override
    public void above(Vanilla anchor, Identifier id, Layer layer) {
        Identifier vanilla = switch (anchor) {
            case CROSSHAIR -> VanillaHudElements.CROSSHAIR;
            case HOTBAR -> VanillaHudElements.HOTBAR;
        };
        HudElementRegistry.attachElementAfter(vanilla, id, layer::render);
    }

    @Override
    public void above(Identifier heavymaticaLayer, Identifier id, Layer layer) {
        HudElementRegistry.attachElementAfter(heavymaticaLayer, id, layer::render);
    }

    @Override
    public void top(Identifier id, Layer layer) {
        HudElementRegistry.addLast(id, layer::render);
    }
}

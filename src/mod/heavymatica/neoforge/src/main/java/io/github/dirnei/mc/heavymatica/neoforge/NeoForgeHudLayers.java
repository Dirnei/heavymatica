package io.github.dirnei.mc.heavymatica.neoforge;

import io.github.dirnei.mc.heavymatica.platform.HudLayers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

final class NeoForgeHudLayers implements HudLayers {

    private final List<Consumer<RegisterGuiLayersEvent>> pending = new ArrayList<>();

    @Override
    public void above(Vanilla anchor, Identifier id, Layer layer) {
        Identifier vanilla = switch (anchor) {
            case CROSSHAIR -> VanillaGuiLayers.CROSSHAIR;
            case HOTBAR -> VanillaGuiLayers.HOTBAR;
        };
        pending.add(event -> event.registerAbove(vanilla, id, layer::render));
    }

    @Override
    public void above(Identifier heavymaticaLayer, Identifier id, Layer layer) {
        pending.add(event -> event.registerAbove(heavymaticaLayer, id, layer::render));
    }

    @Override
    public void top(Identifier id, Layer layer) {
        pending.add(event -> event.registerAboveAll(id, layer::render));
    }

    void registerAll(RegisterGuiLayersEvent event) {
        pending.forEach(registration -> registration.accept(event));
    }
}

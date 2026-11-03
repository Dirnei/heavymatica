package io.github.dirnei.mc.heavymatica.platform;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** HUD layers, drawn in the order their anchors give. Register them while the client is constructed. */
public interface HudLayers {

    void above(Vanilla anchor, Identifier id, Layer layer);

    /** Above another Heavymatica layer, which must be registered first. */
    void above(Identifier heavymaticaLayer, Identifier id, Layer layer);

    /** Above every other layer. */
    void top(Identifier id, Layer layer);

    enum Vanilla {
        CROSSHAIR,
        HOTBAR,
    }

    @FunctionalInterface
    interface Layer {
        void render(GuiGraphicsExtractor graphics, DeltaTracker delta);
    }
}

package io.github.dirnei.mc.heavymatica.ui.glass;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A square button with a gear icon; its message names it for narration and tooltips. */
public final class GearButton extends GlassButton {

    public GearButton(int x, int y, int size, Component message, Consumer<GlassButton> onPress) {
        super(x, y, size, size, message, Kind.SECONDARY, onPress);
    }

    @Override
    protected void extractLabel(GuiGraphicsExtractor graphics, Font font, int color) {
        double cx = getX() + width / 2.0;
        double cy = getY() + height / 2.0;
        int icon = isHovered() || isFocused() ? GlassStyle.TEXT : GlassStyle.LABEL;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            double tx = cx + Math.cos(angle) * 4.5;
            double ty = cy + Math.sin(angle) * 4.5;
            Glass.fill(graphics, tx - 1.25, ty - 1.25, 2.5, 2.5, 0.6, icon);
        }
        Glass.rim(graphics, cx - 3.75, cy - 3.75, 7.5, 7.5, 3.75, 2, icon);
        if (isHovered()) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, getMessage(), getX(), getY() + height + 2);
        }
    }
}

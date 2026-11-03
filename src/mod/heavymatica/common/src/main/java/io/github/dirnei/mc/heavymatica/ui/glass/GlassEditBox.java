package io.github.dirnei.mc.heavymatica.ui.glass;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A text field drawn as a glass field, optionally with a search icon. The vanilla box sits inside the field without
 * its own border; clicks anywhere on the field reach it.
 */
public final class GlassEditBox extends EditBox {

    public static final int HEIGHT = 18;
    private static final int PAD = 6;
    private static final int ICON = 12;

    private final int insetX;
    private final int insetY;
    private final int fieldWidth;
    private final int fieldHeight;
    private final boolean search;

    public GlassEditBox(Font font, int x, int y, int width, int height, Component hint, boolean search) {
        super(font, x + PAD + (search ? ICON : 0), y + (height - 8) / 2, Math.max(8, width - 2 * PAD - (search ? ICON : 0)), 9, hint);
        this.insetX = PAD + (search ? ICON : 0);
        this.insetY = (height - 8) / 2;
        this.fieldWidth = width;
        this.fieldHeight = height;
        this.search = search;
        setBordered(false);
        setTextColor(GlassStyle.TEXT);
        setHint(hint.copy().withColor(GlassStyle.HINT & 0xFFFFFF));
    }

    /** Moves the whole field so its outer frame starts at {@code (x, y)}. */
    public void moveTo(int x, int y) {
        setX(x + insetX);
        setY(y + insetY);
    }

    private int fieldX() {
        return getX() - insetX;
    }

    private int fieldY() {
        return getY() - insetY;
    }

    public int fieldWidth() {
        return fieldWidth;
    }

    public int fieldHeight() {
        return fieldHeight;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int fieldX = fieldX();
        int fieldY = fieldY();
        return visible && mouseX >= fieldX && mouseX < fieldX + fieldWidth && mouseY >= fieldY && mouseY < fieldY + fieldHeight;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (!isVisible()) {
            return;
        }
        int rim = isFocused() ? 0x8C7FD8FF : isMouseOver(mouseX, mouseY) ? 0x3DFFFFFF : 0x24FFFFFF;
        int fieldX = fieldX();
        int fieldY = fieldY();
        Glass.box(graphics, fieldX, fieldY, fieldWidth, fieldHeight, 6, GlassStyle.FIELD, rim);
        if (search) {
            int cx = fieldX + PAD + 3;
            int cy = fieldY + fieldHeight / 2 - 1;
            Glass.rim(graphics, cx - 3, cy - 3, 7, 7, 3.5, 1, GlassStyle.HINT);
            graphics.fill(cx + 3, cy + 3, cx + 5, cy + 5, GlassStyle.HINT);
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
    }
}

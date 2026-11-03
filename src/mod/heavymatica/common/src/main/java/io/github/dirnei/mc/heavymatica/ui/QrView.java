package io.github.dirnei.mc.heavymatica.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws a {@link QrMatrix}'s dark modules with whole-pixel modules, so it stays sharp at every GUI scale. The light
 * background is the caller's.
 */
final class QrView {

    private static final int DARK = 0xFF0E1218;
    private static final int DARK_FADED = 0x800E1218;

    private final QrMatrix matrix;

    QrView(QrMatrix matrix) {
        this.matrix = matrix;
    }

    /** Modules per side, quiet zone included. */
    int modules() {
        return matrix.size();
    }

    /** The side length in GUI pixels at this module size. */
    int side(int module) {
        return matrix.size() * module;
    }

    void draw(GuiGraphicsExtractor graphics, int x, int y, int module, boolean faded) {
        int color = faded ? DARK_FADED : DARK;
        for (QrMatrix.Run run : matrix.runs()) {
            int left = x + run.x() * module;
            int top = y + run.y() * module;
            graphics.fill(left, top, left + run.length() * module, top + module, color);
        }
    }
}

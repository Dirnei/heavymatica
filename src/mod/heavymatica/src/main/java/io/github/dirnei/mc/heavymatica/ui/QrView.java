package io.github.dirnei.mc.heavymatica.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws a {@link QrMatrix} dark on white with whole-pixel modules, so it stays sharp at every GUI scale. */
final class QrView {

    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF000000;

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

    void draw(GuiGraphicsExtractor graphics, int x, int y, int module) {
        int side = side(module);
        graphics.fill(x, y, x + side, y + side, LIGHT);
        for (QrMatrix.Run run : matrix.runs()) {
            int left = x + run.x() * module;
            int top = y + run.y() * module;
            graphics.fill(left, top, left + run.length() * module, top + module, DARK);
        }
    }
}

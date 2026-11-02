package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.List;

/**
 * Where the details page's parts go: the way back, the large preview with the fact tiles under it, and beside them the
 * name, actions and the block list with its filter.
 */
record EntryDetailsLayout(Rect screen, Rect back, Rect preview, Rect facts, Rect side, Rect actions, Rect filter, Rect list) {

    static final int FACT_ROWS = 2;
    static final int FACT_HEIGHT = 30;

    static EntryDetailsLayout of(int width, int height) {
        int m = LibraryLayout.margin(width);
        Rect screen = new Rect(0, 0, width, height);
        Rect back = new Rect(m, m + 2, 70, 18);
        int bodyTop = m + LibraryLayout.HEADER + 8;
        Rect body = new Rect(m, bodyTop, width - 2 * m, Math.max(0, height - bodyTop - m));
        int sideWidth = Math.clamp(Math.round(width * 0.38f), 190, 340);
        Rect side = body.rightPart(sideWidth);
        Rect left = body.leftOf(sideWidth, 8);
        int factsHeight = FACT_ROWS * FACT_HEIGHT + (FACT_ROWS - 1) * 6;
        Rect facts = left.bottomPart(factsHeight);
        Rect preview = left.aboveBottom(factsHeight, 8);
        Rect inner = side.inset(10);
        Rect actions = new Rect(inner.x(), inner.y() + 34, inner.w(), 42);
        Rect filter = new Rect(inner.x(), actions.bottom() + 10, inner.w(), 16);
        Rect list = new Rect(inner.x(), filter.bottom() + 6, inner.w(), Math.max(0, inner.bottom() - filter.bottom() - 6));
        return new EntryDetailsLayout(screen, back, preview, facts, side, actions, filter, list);
    }

    List<Rect> parts() {
        return List.of(back, preview, facts, side);
    }
}

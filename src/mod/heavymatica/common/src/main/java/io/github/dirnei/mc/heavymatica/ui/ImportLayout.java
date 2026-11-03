package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Where the import screen's parts go: header with the way back, the main panel with the first-run intro, the file
 * checklist and the action row, and beside it, on the first run and when there is room, the word on capturing.
 */
record ImportLayout(Rect screen, Rect header, Rect back, Rect main, Rect intro, Rect list, Rect actions, @Nullable Rect capture) {

    static final int INTRO = 52;

    static ImportLayout of(int width, int height, boolean firstRun) {
        int m = LibraryLayout.margin(width);
        Rect screen = new Rect(0, 0, width, height);
        Rect header = new Rect(m, m, width - 2 * m, LibraryLayout.HEADER);
        Rect back = header.rightPart(70).inset(0, 2);
        int bodyTop = header.bottom() + 8;
        Rect body = new Rect(m, bodyTop, width - 2 * m, Math.max(0, height - bodyTop - m));
        Rect capture = null;
        Rect main = body;
        if (firstRun && width >= 520) {
            int captureWidth = Math.clamp(Math.round(width * 0.28f), 150, 240);
            capture = body.rightPart(captureWidth);
            main = body.leftOf(captureWidth, 8);
        }
        Rect inner = main.inset(12);
        Rect intro = firstRun ? inner.top(INTRO) : new Rect(inner.x(), inner.y(), inner.w(), 0);
        Rect actions = inner.bottomPart(20);
        int listTop = intro.bottom() + (intro.h() > 0 ? 8 : 0) + 12;
        Rect list = new Rect(inner.x(), listTop, inner.w() - 6, Math.max(0, actions.y() - 8 - listTop));
        return new ImportLayout(screen, header, back, main, intro, list, actions, capture);
    }

    List<Rect> parts() {
        List<Rect> parts = new ArrayList<>(List.of(back, main));
        if (capture != null) {
            parts.add(capture);
        }
        return parts;
    }
}

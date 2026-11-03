package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.List;

/**
 * Where the account screen's parts go: the way back, the profile panel with name, storage and sign out, and beside it
 * the sign-ins panel with its list.
 */
record AccountLayout(Rect screen, Rect back, Rect profile, Rect signIns, Rect linkAnother, Rect list) {

    static final int ROW = 28;
    static final int LINK = 120;

    static AccountLayout of(int width, int height) {
        int m = LibraryLayout.margin(width);
        Rect screen = new Rect(0, 0, width, height);
        Rect back = new Rect(m, m + 2, 70, 18);
        int bodyTop = m + LibraryLayout.HEADER + 8;
        Rect body = new Rect(m, bodyTop, width - 2 * m, Math.max(0, height - bodyTop - m));
        int profileWidth = Math.clamp(Math.round(width * 0.36f), 160, 300);
        Rect profile = body.leftPart(profileWidth);
        Rect signIns = body.rightOf(profileWidth, 8);
        Rect inner = signIns.inset(10);
        Rect linkAnother = new Rect(inner.right() - LINK, inner.y(), LINK, 18);
        Rect list = new Rect(inner.x(), inner.y() + 34, inner.w(), Math.max(0, inner.h() - 34 - 22));
        return new AccountLayout(screen, back, profile, signIns, linkAnother, list);
    }

    List<Rect> parts() {
        return List.of(back, profile, signIns);
    }

    /** How many sign-in rows fit the list without scrolling. */
    int visibleRows() {
        return Math.max(1, list.h() / ROW);
    }
}

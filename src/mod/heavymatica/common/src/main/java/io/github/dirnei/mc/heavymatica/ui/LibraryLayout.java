package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.List;

/**
 * Where the library's parts go on a screen of a given size, in GUI units: the header with title and controls, the grid
 * area with its chip row, status line and cards, and the details panel beside it.
 *
 * @param account the account chip, zero-sized when the cloud is not shown
 */
record LibraryLayout(Rect screen, Rect title, Rect search, Rect importButton, Rect account, Rect settings, Rect gridPanel, Rect chips, Rect cards,
                     Rect status, Rect details) {

    static final int HEADER = 22;
    static final int CHIP_ROW = 14;
    static final int STATUS = 10;
    static final int MIN_CARD = 76;
    static final int CARD_GAP = 6;
    static final int CARD_HEIGHT = 92;
    static final int BUTTON = 18;
    static final int IMPORT = 58;
    static final int ACCOUNT = 96;
    static final int MIN_SEARCH = 70;
    static final int MAX_SEARCH = 200;
    static final int MIN_TITLE = 70;

    static int margin(int width) {
        return width >= 640 ? 14 : 8;
    }

    static int detailsWidth(int width) {
        return Math.clamp(Math.round(width * 0.3f), 170, 260);
    }

    static LibraryLayout of(int width, int height, boolean cloud) {
        int m = margin(width);
        Rect screen = new Rect(0, 0, width, height);
        Rect header = new Rect(m, m, width - 2 * m, HEADER);
        int controlsY = header.y() + (HEADER - BUTTON) / 2;
        int right = header.right();
        Rect settings = new Rect(right - BUTTON, controlsY, BUTTON, BUTTON);
        right = settings.x() - 6;
        Rect account = cloud ? new Rect(right - ACCOUNT, controlsY, ACCOUNT, BUTTON) : new Rect(right, controlsY, 0, 0);
        right = cloud ? account.x() - 6 : right;
        Rect importButton = new Rect(right - IMPORT, controlsY, IMPORT, BUTTON);
        right = importButton.x() - 6;
        int searchWidth = Math.clamp(right - header.x() - MIN_TITLE - 8, MIN_SEARCH, MAX_SEARCH);
        Rect search = new Rect(right - searchWidth, controlsY, searchWidth, BUTTON);
        Rect title = new Rect(header.x(), header.y(), Math.max(0, search.x() - 8 - header.x()), HEADER);

        int bodyTop = header.bottom() + 8;
        Rect body = new Rect(m, bodyTop, width - 2 * m, Math.max(0, height - bodyTop - m));
        int detailsWidth = detailsWidth(width);
        Rect details = body.rightPart(detailsWidth);
        Rect gridPanel = body.leftOf(detailsWidth, 8);
        Rect inner = gridPanel.inset(8);
        Rect chips = inner.top(CHIP_ROW);
        Rect status = inner.bottomPart(STATUS);
        Rect cards = new Rect(inner.x(), chips.bottom() + 6, inner.w(), Math.max(0, status.y() - 4 - chips.bottom() - 6));
        return new LibraryLayout(screen, title, search, importButton, account, settings, gridPanel, chips, cards, status, details);
    }

    /** The parts that must not overlap each other. */
    List<Rect> parts() {
        return List.of(title, search, importButton, account, settings, gridPanel, details);
    }

    int columns() {
        return Math.max(1, (cards.w() + CARD_GAP) / (MIN_CARD + CARD_GAP));
    }

    int cardWidth() {
        int columns = columns();
        return (cards.w() - CARD_GAP * (columns - 1)) / columns;
    }
}

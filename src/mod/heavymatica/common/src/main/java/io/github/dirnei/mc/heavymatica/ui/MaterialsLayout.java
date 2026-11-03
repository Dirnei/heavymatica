package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.ui.glass.Rect;
import java.util.List;

/**
 * Where the materials screen's parts go: the header with search and Done, the bar for the whole placement while it is
 * locked, and the table with its column header and scrolling rows.
 *
 * @param summary zero-sized while the placement is not locked
 */
record MaterialsLayout(Rect screen, Rect title, Rect search, Rect done, Rect summary, Rect table, Rect columns, Rect rows) {

    static final int SUMMARY = 34;
    static final int COLUMN_HEADER = 14;

    static MaterialsLayout of(int width, int height, boolean locked) {
        int m = LibraryLayout.margin(width);
        Rect screen = new Rect(0, 0, width, height);
        Rect header = new Rect(m, m, width - 2 * m, LibraryLayout.HEADER);
        int controlsY = header.y() + (LibraryLayout.HEADER - LibraryLayout.BUTTON) / 2;
        Rect done = new Rect(header.right() - 52, controlsY, 52, LibraryLayout.BUTTON);
        int searchWidth = Math.clamp(done.x() - 6 - header.x() - LibraryLayout.MIN_TITLE - 8, LibraryLayout.MIN_SEARCH, 180);
        Rect search = new Rect(done.x() - 6 - searchWidth, controlsY, searchWidth, LibraryLayout.BUTTON);
        Rect title = new Rect(header.x(), header.y(), Math.max(0, search.x() - 8 - header.x()), LibraryLayout.HEADER);
        int y = header.bottom() + 8;
        Rect summary = locked ? new Rect(m, y, width - 2 * m, SUMMARY) : new Rect(m, y, width - 2 * m, 0);
        if (locked) {
            y = summary.bottom() + 6;
        }
        Rect table = new Rect(m, y, width - 2 * m, Math.max(0, height - y - m));
        Rect inner = table.inset(10, 6);
        Rect columns = inner.top(COLUMN_HEADER);
        Rect rows = inner.belowTop(COLUMN_HEADER, 2);
        return new MaterialsLayout(screen, title, search, done, summary, table, columns, new Rect(rows.x(), rows.y(), rows.w() - 6, rows.h()));
    }

    List<Rect> parts() {
        return summary.h() > 0 ? List.of(title, search, done, summary, table) : List.of(title, search, done, table);
    }

    /** Column x positions inside the rows: name, count, bar and short, from the left; bar and short only while locked. */
    record Columns(int name, int nameWidth, int countRight, int bar, int barWidth, int shortRight) {
    }

    Columns columnsFor(boolean locked) {
        Rect r = rows;
        if (!locked) {
            int countRight = r.right();
            return new Columns(r.x(), r.w() - 50, countRight, 0, 0, 0);
        }
        int shortRight = r.right();
        int countWidth = 36;
        int shortWidth = 36;
        int flexible = r.w() - countWidth - shortWidth - 3 * 12;
        int nameWidth = Math.max(60, flexible * 42 / 100);
        int barWidth = Math.max(40, flexible - nameWidth);
        int countRight = r.x() + nameWidth + 12 + countWidth;
        int bar = countRight + 12;
        return new Columns(r.x(), nameWidth, countRight, bar, barWidth, shortRight);
    }
}

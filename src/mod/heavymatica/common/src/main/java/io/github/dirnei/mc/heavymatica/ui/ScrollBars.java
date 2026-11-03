package io.github.dirnei.mc.heavymatica.ui;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Drawing for {@link ScrollBar}, and the search match the list screens share. */
final class ScrollBars {

    private ScrollBars() {
    }

    static void draw(GuiGraphicsExtractor graphics, ScrollBar bar, int x, int top, int content, int view, int mouseX, int mouseY) {
        if (!bar.visible(content, view)) {
            return;
        }
        graphics.fill(x, top, x + ScrollBar.WIDTH, top + view, 0x60000000);
        int thumbTop = bar.thumbTop(top, content, view);
        int thumbBottom = thumbTop + bar.thumbHeight(content, view);
        boolean hot = bar.dragging() || mouseX >= x && mouseX < x + ScrollBar.WIDTH && mouseY >= thumbTop && mouseY < thumbBottom;
        graphics.fill(x, thumbTop, x + ScrollBar.WIDTH, thumbBottom, hot ? 0xFFE0E0E0 : 0xFFA0A0A0);
    }

    static boolean onTrack(double mouseX, int x) {
        return mouseX >= x && mouseX < x + ScrollBar.WIDTH;
    }

    /** Whether a name or ID contains the search text, ignoring case. */
    static boolean matches(String query, String name, String id) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || name.toLowerCase(Locale.ROOT).contains(needle) || id.contains(needle);
    }
}

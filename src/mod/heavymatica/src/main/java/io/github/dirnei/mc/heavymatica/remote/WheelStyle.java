package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;
import io.github.dirnei.mc.heavymatica.ui.glass.GlassStyle;

/** The wheel's colours (ARGB) and which entries are tinted as danger or confirm. */
public final class WheelStyle {

    public enum Kind {
        NORMAL(GlassStyle.ACCENT),
        DANGER(GlassStyle.DANGER),
        CONFIRM(GlassStyle.CONFIRM);

        private final int accent;

        Kind(int accent) {
            this.accent = accent;
        }

        public int accent() {
            return accent;
        }
    }

    public static final int BODY_INNER = GlassStyle.BODY_BOTTOM;
    public static final int BODY_OUTER = GlassStyle.BODY_TOP;
    public static final int RIM = 0x12FFFFFF;
    public static final int RIM_HOVERED = GlassStyle.RIM_HOVERED;
    public static final int CENTRE = GlassStyle.CENTRE;
    public static final int DIM_CENTRE = 0x6104060A;
    public static final int DIM_EDGE = 0x1F04060A;
    public static final int HOVER_FILL_INNER = 0x1A;
    public static final int HOVER_FILL_OUTER = 0x6B;
    public static final int TEXT = GlassStyle.TEXT;
    public static final int LABEL = GlassStyle.LABEL;
    public static final int HINT = GlassStyle.HINT;
    public static final int SUBTITLE = GlassStyle.SUBTITLE;
    public static final int GHOST = GlassStyle.ACCENT;
    public static final int GHOST_LIGHT = GlassStyle.GHOST_LIGHT;

    private WheelStyle() {
    }

    public static Kind kind(Entry entry) {
        return switch (entry) {
            case REMOVE, CANCEL, CANCEL_CAPTURE, NO, UNDO_PASTE -> Kind.DANGER;
            case YES, BRING_BACK -> Kind.CONFIRM;
            default -> Kind.NORMAL;
        };
    }

    /** {@code from} blended towards {@code to} by {@code t}, per channel including alpha. */
    public static int mix(int from, int to, double t) {
        double k = Math.max(0, Math.min(1, t));
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 0xFF;
            int b = (to >>> shift) & 0xFF;
            result |= ((int) Math.round(a + (b - a) * k) & 0xFF) << shift;
        }
        return result;
    }

    /** {@code color} with its alpha replaced by {@code alpha} (0 to 255). */
    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}

package io.github.dirnei.mc.heavymatica.remote;

import io.github.dirnei.mc.heavymatica.remote.RadialMenu.Entry;

/** The wheel's colours (ARGB) and which entries are tinted as danger or confirm. */
public final class WheelStyle {

    public enum Kind {
        NORMAL(0xFF7FD8FF),
        DANGER(0xFFFF6B6B),
        CONFIRM(0xFF7EE28F);

        private final int accent;

        Kind(int accent) {
            this.accent = accent;
        }

        public int accent() {
            return accent;
        }
    }

    public static final int BODY_INNER = 0xC70E1218;
    public static final int BODY_OUTER = 0xCC1C232C;
    public static final int RIM = 0x12FFFFFF;
    public static final int RIM_HOVERED = 0x5EFFFFFF;
    public static final int CENTRE = 0xDB080B0F;
    public static final int DIM_CENTRE = 0x6104060A;
    public static final int DIM_EDGE = 0x1F04060A;
    public static final int HOVER_FILL_INNER = 0x1A;
    public static final int HOVER_FILL_OUTER = 0x6B;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int LABEL = 0xFFC9D2DA;
    public static final int HINT = 0xFF9AA7B3;
    public static final int SUBTITLE = 0xFF7F8C98;
    public static final int GHOST = 0xFF7FD8FF;
    public static final int GHOST_LIGHT = 0xFFF1EFE6;

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

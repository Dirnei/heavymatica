package io.github.dirnei.mc.heavymatica.ui.glass;

/** The colours (ARGB) of Heavymatica's screens, HUD panels and the placement wheel. */
public final class GlassStyle {

    public static final int BODY_TOP = 0xCC1C232C;
    public static final int BODY_BOTTOM = 0xC70E1218;
    public static final int DIALOG_TOP = 0xF71C232C;
    public static final int DIALOG_BOTTOM = 0xF70E1218;
    public static final int RIM = 0x14FFFFFF;
    public static final int RIM_STRONG = 0x2EFFFFFF;
    public static final int RIM_HOVERED = 0x5EFFFFFF;
    public static final int CENTRE = 0xDB080B0F;
    public static final int FIELD = 0xB8080B0F;
    public static final int CARD = 0x0AFFFFFF;
    public static final int CARD_HOVERED = 0x14FFFFFF;
    public static final int WELL = 0x47000000;
    public static final int DIM = 0x9E04060A;
    public static final int SCREEN_DIM = 0x8C06080C;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int LABEL = 0xFFC9D2DA;
    public static final int HINT = 0xFF9AA7B3;
    public static final int SUBTITLE = 0xFF7F8C98;
    public static final int ON_ACCENT = 0xFF0E1218;

    /** The ghost cyan: plans, the accent of buttons and selections, and what the player carries. */
    public static final int ACCENT = 0xFF7FD8FF;
    public static final int GHOST_LIGHT = 0xFFF1EFE6;
    public static final int DANGER = 0xFFFF6B6B;
    public static final int DANGER_TEXT = 0xFFFF8A8A;
    public static final int CONFIRM = 0xFF7EE28F;
    public static final int BUILT = 0xFF7EE28F;
    public static final int CARRIED = ACCENT;
    public static final int SHORT = 0xFFFF7A45;
    public static final int SHORT_TEXT = 0xFFFF9A6B;
    public static final int WARNING = 0xFFFFD66B;
    public static final int MISTAKE_RED = 0xFFFF6B6B;
    public static final int MISTAKE_ORANGE = 0xFFFFA77A;
    public static final int MISTAKE_CYAN = 0xFF40E8F8;
    public static final int MISTAKE_MAGENTA = 0xFFE99BFF;

    private GlassStyle() {
    }

    /** {@code color} with its alpha replaced by {@code alpha} (0 to 255). */
    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }
}

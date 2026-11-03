package io.github.dirnei.mc.heavymatica.remote;

import java.util.Arrays;

/**
 * The wheel's motion, free of the game: every value comes from the time in milliseconds, so nothing ever waits on it.
 * Switched off, every value is at its target at once.
 */
public final class WheelAnimation {

    static final long OPEN_FADE = 170;
    static final long OPEN_SCALE = 220;
    static final long ARRIVAL = 180;
    static final long PAGE_OUT = 150;
    static final long PAGE_IN = 220;
    static final long CLOSE = 170;
    static final double HOVER_TIME = 50;
    static final double ACCENT_TIME = 45;
    static final double ACCENT_SHOW_TIME = 45;
    static final double HOVER_PUSH = 5;
    static final double PAGE_TURN = 0.22;
    private static final double SETTLED = 0.01;

    private final boolean enabled;
    private final long openedAt;
    private long pageAt;
    private long lastUpdate;
    private double[] hover = new double[0];
    private double accent = Double.NaN;
    private double accentShown;
    private long previousAt = Long.MIN_VALUE;
    private double[] previousHover = new double[0];

    public WheelAnimation(boolean enabled, long now) {
        this.enabled = enabled;
        this.openedAt = now;
        this.pageAt = now;
        this.lastUpdate = now;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Moves hover and accent towards the segment under the mouse; call once per frame. */
    public void update(long now, int count, int hovered) {
        if (hover.length != count) {
            hover = Arrays.copyOf(hover, count);
        }
        double dt = Math.max(0, now - lastUpdate);
        lastUpdate = now;
        for (int i = 0; i < count; i++) {
            hover[i] = approach(hover[i], i == hovered ? 1 : 0, dt, HOVER_TIME);
        }
        if (hovered >= 0 && hovered < count) {
            double target = WheelGeometry.middle(hovered, count);
            if (Double.isNaN(accent) || accentShown < 0.02 || !enabled) {
                accent = target;
            } else {
                double left = shortest(target - accent) * Math.exp(-dt / ACCENT_TIME);
                accent = Math.abs(left) < 1e-3 ? target : target - left;
            }
        }
        accentShown = approach(accentShown, hovered >= 0 ? 1 : 0, dt, ACCENT_SHOW_TIME);
    }

    /** The page changed: the old ring turns away while the new one arrives. */
    public void turnPage(long now) {
        previousHover = hover.clone();
        previousAt = now;
        pageAt = now;
        lastUpdate = now;
        hover = new double[0];
        accent = Double.NaN;
        accentShown = 0;
    }

    public double openAlpha(long now) {
        return easeOut(progress(now - openedAt, OPEN_FADE));
    }

    public double openScale(long now) {
        return 0.88 + 0.12 * easeBack(progress(now - openedAt, OPEN_SCALE));
    }

    /** How far segment {@code index} has arrived on the current page, from 0 to 1. */
    public double arrival(int index, int count, long now) {
        return easeOut(progress(now - pageAt - (long) (index * stagger(count)), ARRIVAL));
    }

    /** Milliseconds between two segments arriving; a full wheel still arrives within 250 ms. */
    static double stagger(int count) {
        return Math.min(22, 60.0 / Math.max(1, count));
    }

    public double hover(int index) {
        return index >= 0 && index < hover.length ? hover[index] : 0;
    }

    /** The accent's angle clockwise from 12 o'clock, NaN before anything was hovered. */
    public double accentAngle() {
        return accent;
    }

    public double accentShown() {
        return accentShown;
    }

    /** The rotation of the current page as it turns in. */
    public double pageRotation(long now) {
        return previousAt == Long.MIN_VALUE ? 0 : PAGE_TURN * (1 - easeOut(progress(now - pageAt, PAGE_IN)));
    }

    /** Whether the previous page is still turning away. */
    public boolean previousShown(long now) {
        return previousAt != Long.MIN_VALUE && progress(now - previousAt, PAGE_OUT) < 1;
    }

    public double previousAlpha(long now) {
        return 1 - easeOut(progress(now - previousAt, PAGE_OUT));
    }

    public double previousRotation(long now) {
        return -PAGE_TURN * easeOut(progress(now - previousAt, PAGE_OUT));
    }

    public double previousScale(long now) {
        return 1 - 0.08 * progress(now - previousAt, PAGE_OUT);
    }

    public double previousHover(int index) {
        return index >= 0 && index < previousHover.length ? previousHover[index] : 0;
    }

    /** Whether nothing moves any more. */
    public boolean settled(long now) {
        if (openAlpha(now) < 1 || progress(now - pageAt, PAGE_IN + ARRIVAL) < 1 || previousShown(now)) {
            return false;
        }
        for (double h : hover) {
            if (h != 0 && h != 1) {
                return false;
            }
        }
        return accentShown == 0 || accentShown == 1;
    }

    /** The closing ring's alpha {@code elapsed} ms after the wheel closed; 0 when it is gone. */
    public static double closeAlpha(boolean enabled, long elapsed) {
        return enabled ? 1 - easeOut(clamp(elapsed / (double) CLOSE)) : 0;
    }

    public static double closeScale(boolean enabled, long elapsed) {
        return enabled ? 1 + 0.07 * easeOut(clamp(elapsed / (double) CLOSE)) : 1;
    }

    private double progress(long elapsed, long duration) {
        return enabled ? clamp(elapsed / (double) duration) : 1;
    }

    private double approach(double value, double target, double dt, double time) {
        if (!enabled) {
            return target;
        }
        double next = target + (value - target) * Math.exp(-dt / time);
        return Math.abs(next - target) < SETTLED ? target : next;
    }

    /** The same turn as {@code angle}, between -pi and pi. */
    static double shortest(double angle) {
        double a = (angle + Math.PI) % WheelMesh.TAU;
        if (a < 0) {
            a += WheelMesh.TAU;
        }
        return a - Math.PI;
    }

    static double easeOut(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    static double easeBack(double t) {
        double c = 1.4;
        return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}

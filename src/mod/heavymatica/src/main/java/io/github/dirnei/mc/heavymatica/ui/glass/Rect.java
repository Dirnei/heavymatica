package io.github.dirnei.mc.heavymatica.ui.glass;

/** A rectangle in GUI units. */
public record Rect(int x, int y, int w, int h) {

    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public int centreX() {
        return x + w / 2;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** Whether the two share any area. */
    public boolean overlaps(Rect other) {
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    /** Whether this lies fully inside {@code outer}. */
    public boolean inside(Rect outer) {
        return x >= outer.x && y >= outer.y && right() <= outer.right() && bottom() <= outer.bottom();
    }

    public Rect inset(int amount) {
        return new Rect(x + amount, y + amount, Math.max(0, w - 2 * amount), Math.max(0, h - 2 * amount));
    }

    public Rect inset(int horizontal, int vertical) {
        return new Rect(x + horizontal, y + vertical, Math.max(0, w - 2 * horizontal), Math.max(0, h - 2 * vertical));
    }

    /** The top {@code height} of this, and the rest below it after {@code gap}. */
    public Rect top(int height) {
        return new Rect(x, y, w, Math.min(h, height));
    }

    public Rect belowTop(int height, int gap) {
        int taken = Math.min(h, height + gap);
        return new Rect(x, y + taken, w, h - taken);
    }

    public Rect bottomPart(int height) {
        int part = Math.min(h, height);
        return new Rect(x, y + h - part, w, part);
    }

    public Rect aboveBottom(int height, int gap) {
        return new Rect(x, y, w, Math.max(0, h - height - gap));
    }

    public Rect leftPart(int width) {
        return new Rect(x, y, Math.min(w, width), h);
    }

    public Rect rightPart(int width) {
        int part = Math.min(w, width);
        return new Rect(x + w - part, y, part, h);
    }

    public Rect leftOf(int width, int gap) {
        return new Rect(x, y, Math.max(0, w - width - gap), h);
    }

    public Rect rightOf(int width, int gap) {
        int taken = Math.min(w, width + gap);
        return new Rect(x + taken, y, w - taken, h);
    }
}

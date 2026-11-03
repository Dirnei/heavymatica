package io.github.dirnei.mc.heavymatica.ui;

/**
 * The scroll position of a list taller than its view, with a thumb on a track as tall as the view. Pressing the track
 * outside the thumb centres the thumb there; the thumb then follows the mouse until released.
 */
final class ScrollBar {

    static final int WIDTH = 6;
    private static final int MIN_THUMB = 12;

    private double offset;
    private double grab = Double.NaN;

    /** How far the list is scrolled, in pixels. */
    double offset() {
        return offset;
    }

    void reset() {
        offset = 0;
    }

    void scrollBy(double delta, int content, int view) {
        offset = clamped(offset + delta, content, view);
    }

    void clamp(int content, int view) {
        offset = clamped(offset, content, view);
    }

    boolean visible(int content, int view) {
        return content > view;
    }

    int thumbHeight(int content, int view) {
        return content <= 0 ? view : Math.min(view, Math.max(MIN_THUMB, (int) ((long) view * view / content)));
    }

    /** The thumb's top, {@code top} being the track's top. */
    int thumbTop(int top, int content, int view) {
        int travel = view - thumbHeight(content, view);
        int range = content - view;
        return range <= 0 ? top : top + (int) Math.round(offset / range * travel);
    }

    /** Starts dragging when {@code mouseY} is on the track; returns whether it is. */
    boolean press(double mouseY, int top, int content, int view) {
        if (!visible(content, view) || mouseY < top || mouseY >= top + view) {
            return false;
        }
        int thumbTop = thumbTop(top, content, view);
        int thumbHeight = thumbHeight(content, view);
        grab = mouseY >= thumbTop && mouseY < thumbTop + thumbHeight ? mouseY - thumbTop : thumbHeight / 2.0;
        drag(mouseY, top, content, view);
        return true;
    }

    void drag(double mouseY, int top, int content, int view) {
        if (!dragging()) {
            return;
        }
        int travel = view - thumbHeight(content, view);
        double share = travel <= 0 ? 0 : (mouseY - grab - top) / travel;
        offset = clamped(share * (content - view), content, view);
    }

    void release() {
        grab = Double.NaN;
    }

    boolean dragging() {
        return !Double.isNaN(grab);
    }

    private static double clamped(double offset, int content, int view) {
        return Math.max(0, Math.min(offset, Math.max(0, content - view)));
    }
}

package io.github.dirnei.mc.heavymatica.ui.glass;

import java.util.Arrays;

/**
 * Rounded rectangles, pills, rims and segmented bars as coloured quads in GUI units, free of the game. Every edge fades
 * out over a feather, normally one screen pixel ({@code 1 / guiScale}), so shapes look smooth at any GUI scale.
 */
public final class GlassMesh {

    /** The longest arc between two vertices along a rounded corner, in GUI units. */
    private static final double STEP = 1.5;

    private final double feather;
    private float[] xy = new float[256];
    private int[] colors = new int[128];
    private int vertices;

    public GlassMesh(double feather) {
        this.feather = feather;
    }

    public double feather() {
        return feather;
    }

    public int vertexCount() {
        return vertices;
    }

    public float x(int vertex) {
        return xy[vertex * 2];
    }

    public float y(int vertex) {
        return xy[vertex * 2 + 1];
    }

    public int color(int vertex) {
        return colors[vertex];
    }

    public float[] positions() {
        return Arrays.copyOf(xy, vertices * 2);
    }

    public int[] colors() {
        return Arrays.copyOf(colors, vertices);
    }

    public boolean isEmpty() {
        return vertices == 0;
    }

    public void clear() {
        vertices = 0;
    }

    /** {minX, minY, maxX, maxY} of all vertices, or null when empty. */
    public float[] bounds() {
        if (vertices == 0) {
            return null;
        }
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int v = 0; v < vertices; v++) {
            b[0] = Math.min(b[0], x(v));
            b[1] = Math.min(b[1], y(v));
            b[2] = Math.max(b[2], x(v));
            b[3] = Math.max(b[3], y(v));
        }
        return b;
    }

    /** A filled rectangle with corners rounded by {@code radius}, its colour running from {@code top} to {@code bottom}. */
    public void roundRect(double x, double y, double w, double h, double radius, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        Outline outline = outline(x, y, w, h, radius);
        double cx = x + w / 2;
        double cy = y + h / 2;
        int centre = mix(top, bottom, 0.5);
        int n = outline.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ax = outline.x[i];
            double ay = outline.y[i];
            double bx = outline.x[j];
            double by = outline.y[j];
            int ca = mix(top, bottom, (ay - y) / h);
            int cb = mix(top, bottom, (by - y) / h);
            quad(cx, cy, centre, ax, ay, ca, bx, by, cb, bx, by, cb);
            if (feather > 0) {
                quad(ax, ay, ca, bx, by, cb, bx + outline.nx[j] * feather, by + outline.ny[j] * feather, fade(cb, 0),
                        ax + outline.nx[i] * feather, ay + outline.ny[i] * feather, fade(ca, 0));
            }
        }
    }

    public void roundRect(double x, double y, double w, double h, double radius, int color) {
        roundRect(x, y, w, h, radius, color, color);
    }

    /** A pill: a rectangle whose ends are half circles. */
    public void pill(double x, double y, double w, double h, int color) {
        roundRect(x, y, w, h, Math.min(w, h) / 2, color, color);
    }

    /** A line of {@code width} along the inside of a rounded rectangle's edge. */
    public void rim(double x, double y, double w, double h, double radius, double width, int color) {
        if (w <= 0 || h <= 0 || width <= 0) {
            return;
        }
        Outline outline = outline(x, y, w, h, radius);
        int n = outline.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double[] ia = inset(outline, i, width);
            double[] ib = inset(outline, j, width);
            quad(outline.x[i], outline.y[i], color, outline.x[j], outline.y[j], color, ib[0], ib[1], color, ia[0], ia[1], color);
            if (feather > 0) {
                int clear = fade(color, 0);
                quad(outline.x[i], outline.y[i], color, outline.x[j], outline.y[j], color,
                        outline.x[j] + outline.nx[j] * feather, outline.y[j] + outline.ny[j] * feather, clear,
                        outline.x[i] + outline.nx[i] * feather, outline.y[i] + outline.ny[i] * feather, clear);
                double[] fa = inset(outline, i, width + feather);
                double[] fb = inset(outline, j, width + feather);
                quad(ia[0], ia[1], color, ib[0], ib[1], color, fb[0], fb[1], clear, fa[0], fa[1], clear);
            }
        }
    }

    /**
     * A bar split into parts side by side, each as long as its share of {@code total}, with {@code gap} between parts
     * that are drawn. Parts of 0 are left out. Returns each part's start and end x, NaN for parts left out.
     */
    public double[][] segmentedBar(double x, double y, double w, double h, long[] parts, long total, int[] colors, double gap) {
        double[][] spans = spans(x, w, parts, total, gap);
        for (int i = 0; i < parts.length; i++) {
            if (!Double.isNaN(spans[i][0])) {
                double length = spans[i][1] - spans[i][0];
                roundRect(spans[i][0], y, length, h, Math.min(h / 2, length / 2), colors[i]);
            }
        }
        return spans;
    }

    /** Where each part of a segmented bar starts and ends; NaN for parts of 0. Shown parts fill the bar's width up to their share. */
    public static double[][] spans(double x, double w, long[] parts, long total, double gap) {
        double[][] spans = new double[parts.length][];
        int shown = 0;
        for (long part : parts) {
            if (part > 0) {
                shown++;
            }
        }
        double usable = Math.max(0, w - gap * Math.max(0, shown - 1));
        double denominator = Math.max(1, total);
        double at = x;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] <= 0) {
                spans[i] = new double[] {Double.NaN, Double.NaN};
                continue;
            }
            double length = usable * parts[i] / denominator;
            spans[i] = new double[] {at, at + length};
            at += length + gap;
        }
        return spans;
    }

    /** The path around a rounded rectangle, clockwise on screen, with the outward normal at each point. */
    private record Outline(double[] x, double[] y, double[] nx, double[] ny) {

        int size() {
            return x.length;
        }
    }

    private Outline outline(double x, double y, double w, double h, double radius) {
        double r = Math.max(0.5, Math.min(radius, Math.min(w, h) / 2));
        int steps = Math.max(2, (int) Math.ceil(r * Math.PI / 2 / STEP));
        int n = (steps + 1) * 4;
        double[] px = new double[n];
        double[] py = new double[n];
        double[] nx = new double[n];
        double[] ny = new double[n];
        // Corner centres and their start angles (radians, y down), clockwise from the top left.
        double[][] corners = {{x + r, y + r, Math.PI}, {x + w - r, y + r, Math.PI * 1.5}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, Math.PI / 2}};
        int k = 0;
        for (double[] corner : corners) {
            for (int s = 0; s <= steps; s++) {
                double angle = corner[2] + Math.PI / 2 * s / steps;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                px[k] = corner[0] + cos * r;
                py[k] = corner[1] + sin * r;
                nx[k] = cos;
                ny[k] = sin;
                k++;
            }
        }
        return new Outline(px, py, nx, ny);
    }

    private static double[] inset(Outline outline, int i, double distance) {
        return new double[] {outline.x[i] - outline.nx[i] * distance, outline.y[i] - outline.ny[i] * distance};
    }

    private void quad(double x0, double y0, int c0, double x1, double y1, int c1, double x2, double y2, int c2, double x3, double y3, int c3) {
        double[] px = {x0, x1, x2, x3};
        double[] py = {y0, y1, y2, y3};
        int[] pc = {c0, c1, c2, c3};
        double area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += px[i] * py[j] - px[j] * py[i];
        }
        // The GUI draws quads wound like its own rectangles, which have a negative shoelace area in screen space.
        boolean reverse = area > 0;
        ensureCapacity(vertices + 4);
        for (int k = 0; k < 4; k++) {
            int i = reverse ? 3 - k : k;
            xy[vertices * 2] = (float) px[i];
            xy[vertices * 2 + 1] = (float) py[i];
            colors[vertices] = pc[i];
            vertices++;
        }
    }

    private void ensureCapacity(int count) {
        if (count > colors.length) {
            int size = Math.max(count, colors.length * 2);
            colors = Arrays.copyOf(colors, size);
            xy = Arrays.copyOf(xy, size * 2);
        }
    }

    /** {@code color} with its alpha multiplied by {@code factor}. */
    public static int fade(int color, double factor) {
        int alpha = (int) Math.round((color >>> 24) * Math.max(0, Math.min(1, factor)));
        return (alpha << 24) | (color & 0xFFFFFF);
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
}

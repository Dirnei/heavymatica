package io.github.dirnei.mc.heavymatica.remote;

import java.util.Arrays;

/**
 * The wheel's shapes as quads in GUI units, free of the game. Angles run clockwise from 12 o'clock and y grows
 * downwards, as in {@link WheelGeometry}. Every vertex has its own ARGB colour, so gradients cost nothing, and edges
 * fade to transparent over a feather to look smooth at any GUI scale.
 */
public final class WheelMesh {

    public static final double TAU = Math.PI * 2;
    /** The longest arc between two vertices along a curve, in GUI units. */
    private static final double STEP = 3;

    private final double feather;
    private float[] xy = new float[512];
    private int[] colors = new int[256];
    private int vertices;
    private double ox;
    private double oy;
    private double scale = 1;
    private double cos = 1;
    private double sin;

    /** @param feather the width over which edges fade out, normally one screen pixel: {@code 1 / guiScale} */
    public WheelMesh(double feather) {
        this.feather = feather;
    }

    public double feather() {
        return feather;
    }

    /** Places what is added next: rotated by {@code rotation}, scaled by {@code scale}, then moved to {@code (x, y)}. */
    public void transform(double x, double y, double scale, double rotation) {
        this.ox = x;
        this.oy = y;
        this.scale = scale;
        this.cos = Math.cos(rotation);
        this.sin = Math.sin(rotation);
    }

    public int vertexCount() {
        return vertices;
    }

    public int quadCount() {
        return vertices / 4;
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

    /** A filled circle with a soft edge. */
    public void disc(double radius, int color) {
        sector(0, radius, 0, TAU, 0, color, color, feather);
    }

    public void sector(double inner, double outer, double from, double to, double gap, int innerColor, int outerColor) {
        sector(inner, outer, from, to, gap, innerColor, outerColor, feather);
    }

    /**
     * The part of the ring between {@code inner} and {@code outer} from angle {@code from} to {@code to}. Each side edge
     * is moved inwards by half the {@code gap}, so neighbouring sectors stand the same distance apart at every radius.
     * A sweep of a full turn or more has no side edges. The colour runs from {@code innerColor} at the inner radius to
     * {@code outerColor} at the outer one, and every edge fades out over {@code edge}.
     */
    public void sector(double inner, double outer, double from, double to, double gap, int innerColor, int outerColor, double edge) {
        boolean full = to - from >= TAU - 1e-9;
        boolean hasInner = inner > 0;
        double[] radii;
        double[] radiusAlpha;
        int[] radiusColor;
        if (hasInner && edge > 0) {
            radii = new double[] {Math.max(0, inner - edge), inner, outer, outer + edge};
            radiusAlpha = new double[] {0, 1, 1, 0};
            radiusColor = new int[] {innerColor, innerColor, outerColor, outerColor};
        } else if (edge > 0) {
            radii = new double[] {inner, outer, outer + edge};
            radiusAlpha = new double[] {1, 1, 0};
            radiusColor = new int[] {innerColor, outerColor, outerColor};
        } else {
            radii = new double[] {inner, outer};
            radiusAlpha = new double[] {1, 1};
            radiusColor = new int[] {innerColor, outerColor};
        }
        int steps = Math.max(2, (int) Math.ceil(Math.min(to - from, TAU) * (outer + edge) / STEP));
        double half = gap / 2;
        int columns = full ? steps + 1 : steps + 1 + (edge > 0 ? 2 : 0);
        double[][] angles = new double[radii.length][columns];
        double[] columnAlpha = new double[columns];
        for (int row = 0; row < radii.length; row++) {
            double r = radii[row];
            if (full) {
                for (int c = 0; c <= steps; c++) {
                    angles[row][c] = from + TAU * c / steps;
                    columnAlpha[c] = 1;
                }
                continue;
            }
            double start = from + offset(half, r);
            double end = to - offset(half, r);
            if (start > end) {
                start = end = (start + end) / 2;
            }
            int c = 0;
            if (edge > 0) {
                angles[row][c] = Math.min(start, from + offset(half - edge, r));
                columnAlpha[c++] = 0;
            }
            for (int s = 0; s <= steps; s++) {
                angles[row][c] = start + (end - start) * s / steps;
                columnAlpha[c++] = 1;
            }
            if (edge > 0) {
                angles[row][c] = Math.max(end, to - offset(half - edge, r));
                columnAlpha[c] = 0;
            }
        }
        for (int row = 0; row + 1 < radii.length; row++) {
            for (int c = 0; c + 1 < columns; c++) {
                quad(radii[row], angles[row][c], fade(radiusColor[row], radiusAlpha[row] * columnAlpha[c]),
                        radii[row + 1], angles[row + 1][c], fade(radiusColor[row + 1], radiusAlpha[row + 1] * columnAlpha[c]),
                        radii[row + 1], angles[row + 1][c + 1], fade(radiusColor[row + 1], radiusAlpha[row + 1] * columnAlpha[c + 1]),
                        radii[row], angles[row][c + 1], fade(radiusColor[row], radiusAlpha[row] * columnAlpha[c + 1]));
            }
        }
    }

    /** An arc line of width {@code width} centred on {@code radius}, with a soft glow of {@code glow} around it. */
    public void arc(double radius, double width, double from, double to, int color, double glow, int glowColor) {
        if (glow > 0) {
            sector(radius - width / 2, radius + width / 2, from, to, 0, glowColor, glowColor, glow);
        }
        sector(radius - width / 2, radius + width / 2, from, to, 0, color, color, feather);
    }

    /** A plain triangle without a soft edge, given in polar coordinates (radius, angle) pairs. */
    public void triangle(double r1, double a1, double r2, double a2, double r3, double a3, int color) {
        quad(r1, a1, color, r2, a2, color, r3, a3, color, r3, a3, color);
    }

    private static double offset(double distance, double radius) {
        if (radius <= 0) {
            return 0;
        }
        return Math.asin(Math.max(-1, Math.min(1, distance / radius)));
    }

    private void quad(double r0, double a0, int c0, double r1, double a1, int c1, double r2, double a2, int c2, double r3, double a3, int c3) {
        double[] px = {r0 * Math.sin(a0), r1 * Math.sin(a1), r2 * Math.sin(a2), r3 * Math.sin(a3)};
        double[] py = {-r0 * Math.cos(a0), -r1 * Math.cos(a1), -r2 * Math.cos(a2), -r3 * Math.cos(a3)};
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
            double x = px[i] * scale;
            double y = py[i] * scale;
            xy[vertices * 2] = (float) (ox + x * cos - y * sin);
            xy[vertices * 2 + 1] = (float) (oy + x * sin + y * cos);
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
}

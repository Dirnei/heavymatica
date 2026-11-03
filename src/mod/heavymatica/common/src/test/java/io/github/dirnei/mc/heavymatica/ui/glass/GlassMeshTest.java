package io.github.dirnei.mc.heavymatica.ui.glass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GlassMeshTest {

    private static final int OPAQUE = 0xFF102030;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void featherIsOnePhysicalPixelWide(int guiScale) {
        double feather = 1.0 / guiScale;
        GlassMesh mesh = new GlassMesh(feather);
        mesh.roundRect(10, 20, 100, 40, 6, OPAQUE);
        float[] b = mesh.bounds();
        assertEquals(10 - feather, b[0], 1e-4);
        assertEquals(20 - feather, b[1], 1e-4);
        assertEquals(110 + feather, b[2], 1e-4);
        assertEquals(60 + feather, b[3], 1e-4);
    }

    @Test
    void featherVerticesAreTransparentAndInnerOnesOpaque() {
        GlassMesh mesh = new GlassMesh(0.5);
        mesh.roundRect(0, 0, 50, 30, 5, OPAQUE);
        boolean sawClear = false;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            double distance = outsideDistance(mesh.x(v), mesh.y(v), 0, 0, 50, 30, 5);
            if (distance > 1e-3) {
                assertEquals(0.5, distance, 1e-3);
                assertEquals(0, mesh.color(v) >>> 24, "vertex outside the shape at " + mesh.x(v) + "," + mesh.y(v));
                sawClear = true;
            } else {
                assertEquals(0xFF, mesh.color(v) >>> 24, "vertex inside the shape at " + mesh.x(v) + "," + mesh.y(v));
            }
        }
        assertTrue(sawClear);
    }

    /** How far a point lies outside a rounded rectangle; 0 or less inside. */
    private static double outsideDistance(double px, double py, double x, double y, double w, double h, double r) {
        double qx = Math.abs(px - (x + w / 2)) - (w / 2 - r);
        double qy = Math.abs(py - (y + h / 2)) - (h / 2 - r);
        return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
    }

    @Test
    void cornerPointsLieOnTheRadius() {
        GlassMesh mesh = new GlassMesh(0);
        mesh.roundRect(0, 0, 40, 40, 8, OPAQUE);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            float x = mesh.x(v);
            float y = mesh.y(v);
            if (x < 8 && y < 8 && !(x == 20 && y == 20)) {
                assertEquals(8, Math.hypot(x - 8, y - 8), 1e-3, "top-left corner point " + x + "," + y);
            }
        }
    }

    @Test
    void verticalGradientRunsFromTopToBottom() {
        GlassMesh mesh = new GlassMesh(0);
        mesh.roundRect(0, 0, 10, 100, 0, 0xFF000000, 0xFFFFFFFF);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            int blue = mesh.color(v) & 0xFF;
            assertEquals(Math.round(mesh.y(v) / 100 * 255), blue, 2);
        }
    }

    @Test
    void barPartsAddUpToTheWidthWithGaps() {
        double[][] spans = GlassMesh.spans(0, 200, new long[] {67, 96, 51}, 214, 2);
        assertEquals(0, spans[0][0], 1e-9);
        assertEquals(spans[0][1] + 2, spans[1][0], 1e-9);
        assertEquals(spans[1][1] + 2, spans[2][0], 1e-9);
        assertEquals(200, spans[2][1], 1e-9);
    }

    @Test
    void emptyBarPartsAreLeftOut() {
        double[][] spans = GlassMesh.spans(0, 100, new long[] {50, 0, 50}, 100, 2);
        assertTrue(Double.isNaN(spans[1][0]));
        assertEquals(spans[0][1] + 2, spans[2][0], 1e-9);
        assertEquals(100, spans[2][1], 1e-9);
    }

    @Test
    void partsShorterThanTheTotalLeaveTheRestOfTheTrack() {
        double[][] spans = GlassMesh.spans(0, 100, new long[] {25}, 100, 2);
        assertEquals(25, spans[0][1], 1e-9);
    }
}

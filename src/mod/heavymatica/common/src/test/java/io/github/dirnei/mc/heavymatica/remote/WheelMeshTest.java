package io.github.dirnei.mc.heavymatica.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WheelMeshTest {

    private static final double EPS = 1e-3;

    @Test
    void verticesLieOnTheRingAndItsFeather() {
        WheelMesh mesh = new WheelMesh(0.5);
        mesh.sector(40, 90, 0, Math.PI / 2, 3, 0xFF000000, 0xFFFFFFFF);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            double r = radius(mesh, v);
            assertTrue(near(r, 39.5) || near(r, 40) || near(r, 90) || near(r, 90.5), "radius " + r);
        }
    }

    @Test
    void sidesStayWithinTheSectorAngles() {
        WheelMesh mesh = new WheelMesh(0);
        mesh.sector(40, 90, 0, Math.PI / 2, 0, 0xFF000000, 0xFF000000);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            double a = angle(mesh, v);
            assertTrue(a >= -EPS && a <= Math.PI / 2 + EPS, "angle " + a);
        }
    }

    @Test
    void gapKeepsItsWidthAtInnerAndOuterRadius() {
        WheelMesh mesh = new WheelMesh(0.5);
        double gap = 4;
        mesh.sector(40, 90, 0, Math.PI / 2, gap, 0xFF000000, 0xFF000000);
        boolean inner = false;
        boolean outer = false;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            if ((mesh.color(v) >>> 24) != 255) {
                continue;
            }
            // Distance from the radial line at angle 0, which points straight up.
            double distance = mesh.x(v);
            if (distance < gap / 2 + 0.5) {
                assertEquals(gap / 2, distance, EPS);
                double r = radius(mesh, v);
                inner |= near(r, 40);
                outer |= near(r, 90);
            }
        }
        assertTrue(inner && outer);
    }

    @Test
    void featherVerticesAreTransparent() {
        WheelMesh mesh = new WheelMesh(0.5);
        mesh.sector(40, 90, 0, 1, 3, 0xFF112233, 0xFF445566);
        int transparent = 0;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            double r = radius(mesh, v);
            if (near(r, 39.5) || near(r, 90.5)) {
                assertEquals(0, mesh.color(v) >>> 24);
                transparent++;
            }
        }
        assertTrue(transparent > 0);
    }

    @Test
    void colourRunsFromInnerToOuter() {
        WheelMesh mesh = new WheelMesh(0.5);
        mesh.sector(40, 90, 0, 1, 3, 0xFF112233, 0xFF445566);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            double r = radius(mesh, v);
            int rgb = mesh.color(v) & 0xFFFFFF;
            if (near(r, 40)) {
                assertEquals(0x112233, rgb);
            } else if (near(r, 90)) {
                assertEquals(0x445566, rgb);
            }
        }
    }

    @Test
    void aFullTurnHasNoSideEdges() {
        WheelMesh mesh = new WheelMesh(0.5);
        mesh.sector(40, 90, 0, WheelMesh.TAU, 3, 0xFF000000, 0xFF000000);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            double r = radius(mesh, v);
            if (near(r, 40) || near(r, 90)) {
                assertEquals(255, mesh.color(v) >>> 24, "opaque all the way round");
            }
        }
        boolean nearTop = false;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            nearTop |= Math.abs(mesh.x(v)) < EPS && mesh.y(v) < 0;
        }
        assertTrue(nearTop, "no gap at 12 o'clock");
    }

    @Test
    void discReachesTheCentre() {
        WheelMesh mesh = new WheelMesh(0.5);
        mesh.disc(10, 0xFF000000);
        boolean centre = false;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            assertTrue(radius(mesh, v) <= 10.5 + EPS);
            centre |= radius(mesh, v) < EPS;
        }
        assertTrue(centre);
    }

    @Test
    void arcDrawsItsGlowFirst() {
        WheelMesh mesh = new WheelMesh(0.5);
        mesh.arc(97, 2, -0.4, 0.4, 0xFF7FD8FF, 4, 0x667FD8FF);
        int lastGlow = -1;
        int firstLine = -1;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            int alpha = mesh.color(v) >>> 24;
            assertEquals(0x7FD8FF, mesh.color(v) & 0xFFFFFF);
            if (alpha == 0x66) {
                lastGlow = v;
            } else if (alpha == 0xFF && firstLine < 0) {
                firstLine = v;
            }
        }
        assertTrue(lastGlow >= 0 && firstLine > lastGlow);
    }

    @Test
    void quadsAreWoundLikeTheGuiRectangles() {
        WheelMesh mesh = new WheelMesh(0.5);
        mesh.sector(40, 90, 0, 2, 3, 0xFF000000, 0xFF000000);
        mesh.transform(100, 50, 1.2, 0.7);
        mesh.sector(0, 20, 0, WheelMesh.TAU, 0, 0xFF000000, 0xFF000000);
        for (int q = 0; q < mesh.quadCount(); q++) {
            double area = 0;
            for (int i = 0; i < 4; i++) {
                int a = q * 4 + i;
                int b = q * 4 + (i + 1) % 4;
                area += mesh.x(a) * mesh.y(b) - mesh.x(b) * mesh.y(a);
            }
            assertFalse(area > 1e-6, "quad " + q);
        }
    }

    @Test
    void transformMovesScalesAndTurns() {
        WheelMesh mesh = new WheelMesh(0);
        mesh.transform(100, 50, 2, Math.PI / 2);
        mesh.sector(10, 11, 0, WheelMesh.TAU, 0, 0xFF000000, 0xFF000000);
        for (int v = 0; v < mesh.vertexCount(); v++) {
            double r = Math.hypot(mesh.x(v) - 100, mesh.y(v) - 50);
            assertTrue(near(r, 20) || near(r, 22));
        }
    }

    @Test
    void emptyMeshHasNoBounds() {
        assertNull(new WheelMesh(1).bounds());
    }

    @Test
    void fadeScalesOnlyTheAlpha() {
        assertEquals(0x80123456, WheelMesh.fade(0xFF123456, 128 / 255.0));
        assertEquals(0x00123456, WheelMesh.fade(0xFF123456, 0));
    }

    private static double radius(WheelMesh mesh, int v) {
        return Math.hypot(mesh.x(v), mesh.y(v));
    }

    private static double angle(WheelMesh mesh, int v) {
        double a = Math.atan2(mesh.x(v), -mesh.y(v));
        return a < -EPS ? a + WheelMesh.TAU : a;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < EPS;
    }
}

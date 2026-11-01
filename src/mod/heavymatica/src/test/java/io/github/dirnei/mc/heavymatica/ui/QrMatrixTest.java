package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QrMatrixTest {

    private static final String LINK = "https://cloud.heavymatica.net/link/?code=K7QM-3XRP";

    @Test
    void a_link_address_gives_version_3_or_4_with_the_quiet_zone() {
        assertEquals(50, LINK.length());

        QrMatrix matrix = QrMatrix.encode(LINK);

        assertTrue(matrix.version() == 3 || matrix.version() == 4, "version " + matrix.version());
        int modules = 17 + 4 * matrix.version();
        assertEquals(modules + 2 * QrMatrix.QUIET_ZONE, matrix.size());
        for (int i = 0; i < matrix.size(); i++) {
            for (int edge : new int[] {0, QrMatrix.QUIET_ZONE - 1, matrix.size() - QrMatrix.QUIET_ZONE, matrix.size() - 1}) {
                assertFalse(matrix.dark(i, edge));
                assertFalse(matrix.dark(edge, i));
            }
        }
    }

    @Test
    void the_three_finder_patterns_sit_in_their_corners() {
        QrMatrix matrix = QrMatrix.encode(LINK);
        int far = matrix.size() - QrMatrix.QUIET_ZONE - 7;
        int near = QrMatrix.QUIET_ZONE;

        assertFinder(matrix, near, near);
        assertFinder(matrix, far, near);
        assertFinder(matrix, near, far);
    }

    @Test
    void the_runs_cover_exactly_the_dark_modules() {
        QrMatrix matrix = QrMatrix.encode(LINK);
        boolean[][] covered = new boolean[matrix.size()][matrix.size()];

        for (QrMatrix.Run run : matrix.runs()) {
            assertTrue(run.length() > 0);
            for (int x = run.x(); x < run.x() + run.length(); x++) {
                assertFalse(covered[run.y()][x], "covered twice at " + x + "," + run.y());
                covered[run.y()][x] = true;
            }
        }
        for (int y = 0; y < matrix.size(); y++) {
            for (int x = 0; x < matrix.size(); x++) {
                assertEquals(matrix.dark(x, y), covered[y][x], "module " + x + "," + y);
            }
        }
    }

    @Test
    void modules_are_whole_pixels_and_never_vanish() {
        assertEquals(3, QrMatrix.moduleSize(37, 120));
        assertEquals(1, QrMatrix.moduleSize(37, 20));
    }

    /** 7 x 7: a dark ring, a light ring and a dark 3 x 3 centre, with a light separator around it. */
    private static void assertFinder(QrMatrix matrix, int left, int top) {
        for (int dy = 0; dy < 7; dy++) {
            for (int dx = 0; dx < 7; dx++) {
                int ring = Math.max(Math.abs(dx - 3), Math.abs(dy - 3));
                assertEquals(ring != 2, matrix.dark(left + dx, top + dy), "finder at " + left + "," + top + " module " + dx + "," + dy);
            }
        }
        for (int i = -1; i <= 7; i++) {
            assertSeparator(matrix, left + i, top - 1);
            assertSeparator(matrix, left + i, top + 7);
            assertSeparator(matrix, left - 1, top + i);
            assertSeparator(matrix, left + 7, top + i);
        }
    }

    private static void assertSeparator(QrMatrix matrix, int x, int y) {
        assertFalse(matrix.dark(x, y), "separator at " + x + "," + y);
    }
}

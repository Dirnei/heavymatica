package io.github.dirnei.mc.heavymatica.ui;

import io.nayuki.qrcodegen.QrCode;
import java.util.ArrayList;
import java.util.List;

/**
 * A QR code of a text as horizontal runs of dark modules, including the 4-module quiet zone, so drawing it takes one
 * fill per run instead of one per module.
 */
final class QrMatrix {

    static final int QUIET_ZONE = 4;

    /** {@code length} dark modules from column {@code x} in row {@code y}, quiet zone included in both. */
    record Run(int y, int x, int length) {
    }

    private final int version;
    private final int size;
    private final boolean[][] dark;
    private final List<Run> runs;

    private QrMatrix(QrCode code) {
        this.version = code.version;
        this.size = code.size + QUIET_ZONE * 2;
        this.dark = new boolean[size][size];
        List<Run> collected = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            int start = -1;
            for (int x = 0; x <= size; x++) {
                boolean on = x < size && code.getModule(x - QUIET_ZONE, y - QUIET_ZONE);
                if (x < size) {
                    dark[y][x] = on;
                }
                if (on && start < 0) {
                    start = x;
                } else if (!on && start >= 0) {
                    collected.add(new Run(y, start, x - start));
                    start = -1;
                }
            }
        }
        this.runs = List.copyOf(collected);
    }

    /** Encodes with error correction level M. */
    static QrMatrix encode(String text) {
        return new QrMatrix(QrCode.encodeText(text, QrCode.Ecc.MEDIUM));
    }

    int version() {
        return version;
    }

    /** Modules per side, quiet zone included. */
    int size() {
        return size;
    }

    boolean dark(int x, int y) {
        return dark[y][x];
    }

    List<Run> runs() {
        return runs;
    }

    /** The largest whole number of GUI pixels per module that fits, at least one. */
    static int moduleSize(int matrixSize, int available) {
        return Math.max(1, available / matrixSize);
    }
}

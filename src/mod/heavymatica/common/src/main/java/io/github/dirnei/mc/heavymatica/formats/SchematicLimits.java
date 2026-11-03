package io.github.dirnei.mc.heavymatica.formats;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Size limits; must stay identical to the cloud's. */
public final class SchematicLimits {

    public static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    public static final long MAX_DECOMPRESSED_BYTES = 128L * 1024 * 1024;
    public static final int MAX_DIMENSION = 1024;
    public static final long MAX_VOLUME = 16_777_216L;

    private SchematicLimits() {
    }

    /** Rejects dimensions before any block data is decoded. */
    public static void checkDimensions(int width, int height, int length) {
        if (width <= 0 || height <= 0 || length <= 0) {
            throw SchematicRejectedException.malformed("Invalid dimensions " + width + " x " + height + " x " + length + ".");
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || length > MAX_DIMENSION) {
            throw new SchematicRejectedException(RejectionCode.DIMENSIONS_TOO_LARGE,
                    "Dimensions " + width + " x " + height + " x " + length + " exceed " + MAX_DIMENSION + " blocks.");
        }
        long volume = (long) width * height * length;
        if (volume > MAX_VOLUME) {
            throw new SchematicRejectedException(RejectionCode.VOLUME_TOO_LARGE, "Volume " + volume + " exceeds " + MAX_VOLUME + " blocks.");
        }
    }

    /** Passes reads through until the limit, then rejects the file with the given code. */
    static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private final RejectionCode code;
        private long total;

        LimitedInputStream(InputStream in, long limit, RejectionCode code) {
            super(in);
            this.limit = limit;
            this.code = code;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(int read) {
            total += read;
            if (total > limit) {
                throw new SchematicRejectedException(code, "More than " + limit + " bytes.");
            }
        }
    }
}

package io.github.dirnei.mc.heavymatica.formats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/** Mirrors the cloud's NbtReaderTests. */
class NbtTest {

    @Test
    void reads_every_tag_type() throws IOException {
        byte[] bytes = root(out -> {
            tag(out, Nbt.BYTE, "byte").writeByte(0xFE);
            tag(out, Nbt.SHORT, "short").writeShort(-2);
            tag(out, Nbt.INT, "int").writeInt(123_456);
            tag(out, Nbt.LONG, "long").writeLong(-9_000_000_000L);
            tag(out, Nbt.FLOAT, "float").writeFloat(1.5f);
            tag(out, Nbt.DOUBLE, "double").writeDouble(-2.25);
            tag(out, Nbt.BYTE_ARRAY, "bytes").writeInt(3);
            out.write(new byte[] {1, 2, 3});
            tag(out, Nbt.STRING, "string").writeUTF("minecraft:stone");
            tag(out, Nbt.LIST, "list").writeByte(Nbt.INT);
            out.writeInt(2);
            out.writeInt(7);
            out.writeInt(8);
            tag(out, Nbt.LIST, "empty").writeByte(Nbt.END);
            out.writeInt(0);
            tag(out, Nbt.COMPOUND, "nested");
            tag(out, Nbt.INT, "x").writeInt(5);
            out.writeByte(0);
            tag(out, Nbt.INT_ARRAY, "ints").writeInt(2);
            out.writeInt(-1);
            out.writeInt(1);
            tag(out, Nbt.LONG_ARRAY, "longs").writeInt(1);
            out.writeLong(Long.MIN_VALUE);
        });

        Nbt.Compound root = Nbt.readRoot(new ByteArrayInputStream(bytes));

        assertEquals(-2, ((Nbt.Number) root.get("byte")).value());
        assertEquals(-2, ((Nbt.Number) root.get("short")).value());
        assertEquals(123_456, root.integer("int"));
        assertEquals(-9_000_000_000L, ((Nbt.Number) root.get("long")).value());
        assertEquals(1.5, ((Nbt.Floating) root.get("float")).value());
        assertEquals(-2.25, ((Nbt.Floating) root.get("double")).value());
        assertArrayEquals(new byte[] {1, 2, 3}, ((Nbt.ByteArray) root.get("bytes")).value());
        assertEquals("minecraft:stone", root.string("string"));
        assertEquals(List.of(7L, 8L), root.list("list").items().stream().map(item -> ((Nbt.Number) item).value()).toList());
        assertEquals(0, root.list("empty").items().size());
        assertEquals(5, root.compound("nested").integer("x"));
        assertArrayEquals(new int[] {-1, 1}, ((Nbt.IntArray) root.get("ints")).value());
        assertArrayEquals(new long[] {Long.MIN_VALUE}, ((Nbt.LongArray) root.get("longs")).value());
    }

    @Test
    void keeps_compound_entries_in_file_order() throws IOException {
        byte[] bytes = root(out -> {
            tag(out, Nbt.INT, "zeta").writeInt(1);
            tag(out, Nbt.INT, "alpha").writeInt(2);
            tag(out, Nbt.INT, "mid").writeInt(3);
        });

        assertEquals(List.of("zeta", "alpha", "mid"), List.copyOf(Nbt.readRoot(new ByteArrayInputStream(bytes)).entries().keySet()));
    }

    @Test
    void truncated_input_is_malformed() throws IOException {
        byte[] bytes = root(out -> tag(out, Nbt.STRING, "name").writeUTF("minecraft:oak_planks"));

        assertRejected(RejectionCode.MALFORMED_NBT, Arrays.copyOf(bytes, bytes.length - 6));
    }

    @Test
    void negative_lengths_are_malformed() throws IOException {
        assertRejected(RejectionCode.MALFORMED_NBT, root(out -> tag(out, Nbt.INT_ARRAY, "ints").writeInt(-4)));
    }

    @Test
    void nesting_deeper_than_512_levels_is_malformed() throws IOException {
        byte[] bytes = root(out -> {
            for (int i = 0; i < 600; i++) {
                tag(out, Nbt.COMPOUND, "c");
            }
            for (int i = 0; i < 600; i++) {
                out.writeByte(0);
            }
        });

        assertRejected(RejectionCode.MALFORMED_NBT, bytes);
    }

    @Test
    void an_array_longer_than_the_decompression_limit_is_rejected_before_reading_it() throws IOException {
        assertRejected(RejectionCode.DECOMPRESSED_TOO_LARGE, root(out -> tag(out, Nbt.BYTE_ARRAY, "bomb").writeInt(200 * 1024 * 1024)));
    }

    @Test
    void decompression_stops_at_128_MiB() throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(root(out -> {
                byte[] chunk = new byte[1024 * 1024];
                for (int i = 0; i < 130; i++) {
                    tag(out, Nbt.BYTE_ARRAY, "a" + i).writeInt(chunk.length);
                    out.write(chunk);
                }
            }));
        }

        SchematicRejectedException error = assertThrows(SchematicRejectedException.class, () -> SchematicReader.read(compressed.toByteArray()));
        assertEquals(RejectionCode.DECOMPRESSED_TOO_LARGE, error.code());
    }

    private static void assertRejected(RejectionCode code, byte[] bytes) {
        SchematicRejectedException error = assertThrows(SchematicRejectedException.class, () -> Nbt.readRoot(new ByteArrayInputStream(bytes)));
        assertEquals(code, error.code());
    }

    private interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] root(Body body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(Nbt.COMPOUND);
        out.writeUTF("");
        body.write(out);
        out.writeByte(0);
        return bytes.toByteArray();
    }

    private static DataOutputStream tag(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        byte[] encoded = name.getBytes(StandardCharsets.UTF_8);
        out.writeShort(encoded.length);
        out.write(encoded);
        return out;
    }
}

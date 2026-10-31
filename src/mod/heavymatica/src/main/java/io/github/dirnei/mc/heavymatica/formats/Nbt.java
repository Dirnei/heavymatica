package io.github.dirnei.mc.heavymatica.formats;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;
import org.jetbrains.annotations.Nullable;

/**
 * Big-endian (Java edition) NBT, read and written. Independent of Minecraft: its limits must
 * match the cloud's reader exactly, and the formats run without the game.
 */
public final class Nbt {

    public static final int MAX_DEPTH = 512;
    private static final int CHUNK = 1024 * 1024;

    static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7,
            STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    private Nbt() {
    }

    public sealed interface Tag permits Number, Floating, Text, ByteArray, IntArray, LongArray, ListTag, Compound {
    }

    public record Number(long value, int type) implements Tag {
    }

    /** @param type {@code FLOAT} or {@code DOUBLE} */
    public record Floating(double value, int type) implements Tag {
    }

    public record Text(String value) implements Tag {
    }

    public record ByteArray(byte[] value) implements Tag {
    }

    public record IntArray(int[] value) implements Tag {
    }

    public record LongArray(long[] value) implements Tag {
    }

    public record ListTag(int itemType, List<Tag> items) implements Tag {
    }

    /** A compound; keeps entries in file order, which Litematica region merging depends on. */
    public record Compound(LinkedHashMap<String, Tag> entries) implements Tag {

        public @Nullable Tag get(String name) {
            return entries.get(name);
        }

        public boolean contains(String name) {
            return entries.containsKey(name);
        }

        public @Nullable Compound compound(String name) {
            return entries.get(name) instanceof Compound compound ? compound : null;
        }

        public @Nullable ListTag list(String name) {
            return entries.get(name) instanceof ListTag list ? list : null;
        }

        public @Nullable Integer integer(String name) {
            return entries.get(name) instanceof Number number ? Math.toIntExact(number.value()) : null;
        }

        public @Nullable String string(String name) {
            return entries.get(name) instanceof Text text ? text.value() : null;
        }
    }

    /** Reads a named root compound (the name is ignored). Structural problems are malformed_nbt rejections. */
    public static Compound readRoot(InputStream input) {
        try {
            DataInputStream data = new DataInputStream(input);
            int type = data.readUnsignedByte();
            if (type != COMPOUND) {
                throw SchematicRejectedException.malformed("The root tag is not a compound.");
            }
            readString(data);
            return (Compound) readPayload(data, type, 0);
        } catch (EOFException e) {
            throw new SchematicRejectedException(RejectionCode.MALFORMED_NBT, "The NBT ends in the middle of a tag.", e);
        } catch (ZipException e) {
            throw new SchematicRejectedException(RejectionCode.MALFORMED_NBT, "The compressed data is corrupt.", e);
        } catch (IOException e) {
            throw new SchematicRejectedException(RejectionCode.MALFORMED_NBT, "The NBT cannot be read.", e);
        }
    }

    /** Writes a gzip-compressed root compound with an empty name. The same tag always gives the same bytes. */
    public static void writeRoot(Compound root, OutputStream output) throws IOException {
        GZIPOutputStream gzip = new GZIPOutputStream(output, 64 * 1024);
        writeUncompressed(root, gzip);
        gzip.finish();
    }

    public static byte[] writeRoot(Compound root) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            writeRoot(root, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** Writes a root compound with an empty name, without compression. */
    public static void writeUncompressed(Compound root, OutputStream output) throws IOException {
        DataOutputStream data = new DataOutputStream(new BufferedOutputStream(output, 64 * 1024));
        data.writeByte(COMPOUND);
        writeString(data, "");
        writePayload(data, root);
        data.flush();
    }

    /** The NBT type ID of a tag. */
    public static int typeOf(Tag tag) {
        return switch (tag) {
            case Number number -> number.type();
            case Floating floating -> floating.type();
            case Text ignored -> STRING;
            case ByteArray ignored -> BYTE_ARRAY;
            case IntArray ignored -> INT_ARRAY;
            case LongArray ignored -> LONG_ARRAY;
            case ListTag ignored -> LIST;
            case Compound ignored -> COMPOUND;
        };
    }

    private static void writePayload(DataOutputStream data, Tag tag) throws IOException {
        switch (tag) {
            case Number number -> {
                switch (number.type()) {
                    case BYTE -> data.writeByte((int) number.value());
                    case SHORT -> data.writeShort((int) number.value());
                    case INT -> data.writeInt((int) number.value());
                    case LONG -> data.writeLong(number.value());
                    default -> throw new IllegalArgumentException("Not a number type: " + number.type());
                }
            }
            case Floating floating -> {
                if (floating.type() == FLOAT) {
                    data.writeFloat((float) floating.value());
                } else {
                    data.writeDouble(floating.value());
                }
            }
            case Text text -> writeString(data, text.value());
            case ByteArray array -> {
                data.writeInt(array.value().length);
                data.write(array.value());
            }
            case IntArray array -> {
                data.writeInt(array.value().length);
                for (int value : array.value()) {
                    data.writeInt(value);
                }
            }
            case LongArray array -> {
                data.writeInt(array.value().length);
                for (long value : array.value()) {
                    data.writeLong(value);
                }
            }
            case ListTag list -> {
                data.writeByte(list.items().isEmpty() ? list.itemType() : typeOf(list.items().getFirst()));
                data.writeInt(list.items().size());
                for (Tag item : list.items()) {
                    writePayload(data, item);
                }
            }
            case Compound compound -> {
                for (Map.Entry<String, Tag> entry : compound.entries().entrySet()) {
                    data.writeByte(typeOf(entry.getValue()));
                    writeString(data, entry.getKey());
                    writePayload(data, entry.getValue());
                }
                data.writeByte(END);
            }
        }
    }

    private static void writeString(DataOutputStream data, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xFFFF) {
            throw new IllegalArgumentException("A string is longer than NBT allows.");
        }
        data.writeShort(bytes.length);
        data.write(bytes);
    }

    private static Tag readPayload(DataInputStream data, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw SchematicRejectedException.malformed("NBT is nested deeper than " + MAX_DEPTH + " levels.");
        }
        return switch (type) {
            case BYTE -> new Number(data.readByte(), type);
            case SHORT -> new Number(data.readShort(), type);
            case INT -> new Number(data.readInt(), type);
            case LONG -> new Number(data.readLong(), type);
            case FLOAT -> new Floating(data.readFloat(), type);
            case DOUBLE -> new Floating(data.readDouble(), type);
            case BYTE_ARRAY -> new ByteArray(readBytes(data, length(data, 1)));
            case STRING -> new Text(readString(data));
            case LIST -> readList(data, depth);
            case COMPOUND -> readCompound(data, depth);
            case INT_ARRAY -> {
                int count = length(data, 4);
                java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(readBytes(data, count * 4));
                int[] values = new int[count];
                bytes.asIntBuffer().get(values);
                yield new IntArray(values);
            }
            case LONG_ARRAY -> {
                int count = length(data, 8);
                java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(readBytes(data, Math.multiplyExact(count, 8)));
                long[] values = new long[count];
                bytes.asLongBuffer().get(values);
                yield new LongArray(values);
            }
            default -> throw SchematicRejectedException.malformed("Unknown NBT tag type " + type + ".");
        };
    }

    private static ListTag readList(DataInputStream data, int depth) throws IOException {
        int itemType = data.readUnsignedByte();
        int count = data.readInt();
        if (count < 0) {
            throw SchematicRejectedException.malformed("Negative list length.");
        }
        if (itemType == END && count > 0) {
            throw SchematicRejectedException.malformed("A non-empty list has no item type.");
        }
        List<Tag> items = new ArrayList<>(Math.min(count, 4096));
        for (int i = 0; i < count; i++) {
            items.add(readPayload(data, itemType, depth + 1));
        }
        return new ListTag(itemType, items);
    }

    private static Compound readCompound(DataInputStream data, int depth) throws IOException {
        LinkedHashMap<String, Tag> entries = new LinkedHashMap<>();
        while (true) {
            int type = data.readUnsignedByte();
            if (type == END) {
                return new Compound(entries);
            }
            String name = readString(data);
            entries.put(name, readPayload(data, type, depth + 1));
        }
    }

    private static int length(DataInputStream data, int elementSize) throws IOException {
        int length = data.readInt();
        if (length < 0) {
            throw SchematicRejectedException.malformed("Negative array length.");
        }
        // A length the size limit could never deliver; the limited stream catches the rest.
        if ((long) length * elementSize > SchematicLimits.MAX_DECOMPRESSED_BYTES) {
            throw new SchematicRejectedException(RejectionCode.DECOMPRESSED_TOO_LARGE, "An array is larger than the decompression limit.");
        }
        return length;
    }

    private static String readString(DataInputStream data) throws IOException {
        int length = data.readUnsignedShort();
        // Java's modified UTF-8 differs only for NUL and supplementary characters, which block IDs never contain.
        return new String(readBytes(data, length), StandardCharsets.UTF_8);
    }

    /** Grows with the data actually received, so a tiny file cannot claim a huge array and force the allocation. */
    private static byte[] readBytes(DataInputStream data, int count) throws IOException {
        if (count <= CHUNK) {
            byte[] bytes = new byte[count];
            data.readFully(bytes);
            return bytes;
        }
        ByteArrayOutputStream collected = new ByteArrayOutputStream(CHUNK);
        byte[] chunk = new byte[CHUNK];
        for (int remaining = count; remaining > 0; remaining -= CHUNK) {
            int part = Math.min(CHUNK, remaining);
            data.readFully(chunk, 0, part);
            collected.write(chunk, 0, part);
        }
        return collected.toByteArray();
    }

    /** Lists of numbers or an int array of exactly three values, as used for positions and sizes. */
    static int[] intTriple(@Nullable Tag tag, String what) {
        int[] values = switch (tag) {
            case IntArray array -> array.value();
            case ListTag list when list.items().stream().allMatch(item -> item instanceof Number) ->
                    list.items().stream().mapToInt(item -> Math.toIntExact(((Number) item).value())).toArray();
            case null, default -> null;
        };
        if (values == null || values.length != 3) {
            throw SchematicRejectedException.malformed(what + " is not three integers.");
        }
        return values;
    }

    static Map<String, Tag> entriesExcept(Compound compound, String... names) {
        LinkedHashMap<String, Tag> copy = new LinkedHashMap<>(compound.entries());
        for (String name : names) {
            copy.remove(name);
        }
        return copy;
    }
}

package io.github.dirnei.mc.heavymatica.formats;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

/**
 * Reads any supported schematic file into a {@link SchematicModel}, or rejects it with a
 * {@link SchematicRejectedException} carrying the reason code (schematic-formats, D6). Produces the same results as
 * the cloud's reader for every file of the shared conformance set.
 */
public final class SchematicReader {

    private SchematicReader() {
    }

    /** Reads a file from disk, rejecting it on size before reading its content. */
    public static SchematicModel read(Path file) {
        try {
            if (Files.size(file) > SchematicLimits.MAX_FILE_BYTES) {
                throw tooLarge();
            }
            return read(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a complete file. The format is detected from the content, never from a file name. */
    public static SchematicModel read(byte[] file) {
        if (file.length > SchematicLimits.MAX_FILE_BYTES) {
            throw tooLarge();
        }
        Nbt.Compound root = readNbt(file);
        return switch (detect(root)) {
            case LITEMATICA -> LitematicaFormat.read(root);
            case SPONGE -> SpongeFormat.read(root);
            case STRUCTURE -> StructureFormat.read(root);
        };
    }

    private static Nbt.Compound readNbt(byte[] file) {
        InputStream raw = new ByteArrayInputStream(file);
        try {
            if (file.length >= 2 && (file[0] & 0xFF) == 0x1F && (file[1] & 0xFF) == 0x8B) {
                raw = new GZIPInputStream(raw, 64 * 1024);
            } else if (file.length == 0 || file[0] != Nbt.COMPOUND) {
                throw new SchematicRejectedException(RejectionCode.UNSUPPORTED_FORMAT, "The file is neither gzip-compressed nor NBT.");
            }
        } catch (ZipException e) {
            throw new SchematicRejectedException(RejectionCode.MALFORMED_NBT, "The compressed data is corrupt.", e);
        } catch (IOException e) {
            throw new SchematicRejectedException(RejectionCode.MALFORMED_NBT, "The file cannot be read.", e);
        }
        InputStream limited = new SchematicLimits.LimitedInputStream(raw, SchematicLimits.MAX_DECOMPRESSED_BYTES, RejectionCode.DECOMPRESSED_TOO_LARGE);
        return Nbt.readRoot(new BufferedInputStream(limited, 64 * 1024));
    }

    static SchematicFormat detect(Nbt.Compound root) {
        if (root.compound("Regions") != null && (root.contains("MinecraftDataVersion") || root.contains("Version"))) {
            return SchematicFormat.LITEMATICA;
        }
        Nbt.Compound v3 = root.compound("Schematic");
        if (v3 != null && Integer.valueOf(3).equals(v3.integer("Version"))) {
            return SchematicFormat.SPONGE;
        }
        if (Integer.valueOf(2).equals(root.integer("Version")) && root.contains("BlockData")) {
            return SchematicFormat.SPONGE;
        }
        if (root.list("size") != null && root.list("blocks") != null && (root.list("palette") != null || root.list("palettes") != null)) {
            return SchematicFormat.STRUCTURE;
        }
        throw new SchematicRejectedException(RejectionCode.UNSUPPORTED_FORMAT, "The NBT is not a Litematica, Sponge or structure file.");
    }

    /** A block entity in canonical form: its data with the block entity ID under {@code id}. */
    static Nbt.Compound blockEntity(String id, Map<String, Nbt.Tag> data) {
        LinkedHashMap<String, Nbt.Tag> entries = new LinkedHashMap<>();
        entries.put("id", new Nbt.Text(id));
        data.forEach((name, tag) -> {
            if (!name.equals("id") && !name.equals("Id") && !name.equals("Pos") && !name.equals("x") && !name.equals("y") && !name.equals("z")) {
                entries.put(name, tag);
            }
        });
        return new Nbt.Compound(entries);
    }

    private static SchematicRejectedException tooLarge() {
        return new SchematicRejectedException(RejectionCode.FILE_TOO_LARGE, "The file is larger than " + SchematicLimits.MAX_FILE_BYTES + " bytes.");
    }
}

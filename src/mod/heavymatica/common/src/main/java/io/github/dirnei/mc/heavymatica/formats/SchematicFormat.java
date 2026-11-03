package io.github.dirnei.mc.heavymatica.formats;

import java.util.Arrays;
import java.util.Optional;

/** The wire names match the contract's SchematicFormat. */
public enum SchematicFormat {
    LITEMATICA("litematica", "litematic"),
    SPONGE("sponge", "schem"),
    STRUCTURE("structure", "nbt");

    private final String wireName;
    private final String extension;

    SchematicFormat(String wireName, String extension) {
        this.wireName = wireName;
        this.extension = extension;
    }

    public String wireName() {
        return wireName;
    }

    /** The file extension the format's own tools use, without the dot. */
    public String extension() {
        return extension;
    }

    public static Optional<SchematicFormat> ofWireName(String wireName) {
        return Arrays.stream(values()).filter(format -> format.wireName.equals(wireName)).findFirst();
    }
}

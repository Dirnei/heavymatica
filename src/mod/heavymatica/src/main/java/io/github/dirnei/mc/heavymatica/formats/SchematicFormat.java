package io.github.dirnei.mc.heavymatica.formats;

/** The file formats Heavymatica reads. The wire names match the contract's SchematicFormat. */
public enum SchematicFormat {
    LITEMATICA("litematica"),
    SPONGE("sponge"),
    STRUCTURE("structure");

    private final String wireName;

    SchematicFormat(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}

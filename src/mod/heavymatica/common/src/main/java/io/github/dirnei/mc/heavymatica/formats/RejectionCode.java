package io.github.dirnei.mc.heavymatica.formats;

/** Each code matches the contract's ProblemCode of the same wire name. */
public enum RejectionCode {
    UNSUPPORTED_FORMAT("unsupported_format"),
    FILE_TOO_LARGE("file_too_large"),
    DECOMPRESSED_TOO_LARGE("decompressed_too_large"),
    DIMENSIONS_TOO_LARGE("dimensions_too_large"),
    VOLUME_TOO_LARGE("volume_too_large"),
    MALFORMED_NBT("malformed_nbt"),
    PALETTE_INDEX_OUT_OF_RANGE("palette_index_out_of_range"),
    SIZE_MISMATCH("size_mismatch");

    private final String wireName;

    RejectionCode(String wireName) {
        this.wireName = wireName;
    }

    /** The snake_case name used by the contract and the conformance set. */
    public String wireName() {
        return wireName;
    }
}

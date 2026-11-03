package io.github.dirnei.mc.heavymatica.formats;

public final class SchematicRejectedException extends RuntimeException {

    private final RejectionCode code;

    public SchematicRejectedException(RejectionCode code, String message) {
        super(message);
        this.code = code;
    }

    public SchematicRejectedException(RejectionCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public RejectionCode code() {
        return code;
    }

    static SchematicRejectedException malformed(String message) {
        return new SchematicRejectedException(RejectionCode.MALFORMED_NBT, message);
    }
}

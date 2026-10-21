package io.github.dirnei.mc.heavymatica.placement;

/** The schematic version a placement pins: a cloud version or a local-only entry, both identified by content hash. */
public sealed interface PlacementSource {

    String sha256();

    /** A cloud version; later versions of the same schematic never change it. */
    record Cloud(String schematicId, int version, String sha256) implements PlacementSource {
    }

    /** A local-only library entry. */
    record Local(String sha256) implements PlacementSource {
    }
}

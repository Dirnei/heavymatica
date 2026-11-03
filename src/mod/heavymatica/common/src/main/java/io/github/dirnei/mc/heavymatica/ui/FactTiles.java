package io.github.dirnei.mc.heavymatica.ui;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** The fact tiles of the details page, as language key and value; facts the file does not record are left out. */
final class FactTiles {

    record Tile(String labelKey, String value) {
    }

    private FactTiles() {
    }

    /**
     * @param minecraftVersion the version the file was saved in, or null when it records none
     * @param types            how many block types, or null while they are not known
     * @param when             when it was added or uploaded, or null when unknown
     * @param uploaded         whether {@code when} is an upload rather than an import
     */
    static List<Tile> of(String format, String size, long blocks, @Nullable Integer types, @Nullable String minecraftVersion, @Nullable String when,
                         boolean uploaded) {
        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile("heavymatica.details.fact.format", format));
        tiles.add(new Tile("heavymatica.details.fact.size", size));
        if (minecraftVersion != null) {
            tiles.add(new Tile("heavymatica.details.fact.minecraft", minecraftVersion));
        }
        tiles.add(new Tile("heavymatica.details.fact.blocks", Long.toString(blocks)));
        if (types != null) {
            tiles.add(new Tile("heavymatica.details.fact.types", Integer.toString(types)));
        }
        if (when != null) {
            tiles.add(new Tile(uploaded ? "heavymatica.details.fact.uploaded" : "heavymatica.details.fact.added", when));
        }
        return List.copyOf(tiles);
    }
}

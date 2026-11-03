package io.github.dirnei.mc.heavymatica.blocks;

import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a schematic palette entry, as written in its file, into the block state the game shows: upgraded from the file's
 * data version, swapped by the block ID as written, then reduced to the properties the block has and accepts. Knows
 * nothing about the game itself; the upgrader and the block catalogue are given.
 */
public final class BlockResolution {

    /** Upgrades a canonical state string from an older data version to the game's. */
    public interface Upgrader {

        String upgrade(String state, int fromDataVersion);
    }

    /** The blocks the game knows. */
    public interface Catalogue {

        /** The block's properties by name, or null when the game does not know the block. */
        @Nullable Map<String, Property> properties(String blockId);
    }

    /** A block property: the values it accepts. */
    public interface Property {

        boolean accepts(String value);
    }

    /**
     * A resolved palette entry.
     *
     * @param shown      the canonical state string as shown: for a known block only the properties it keeps, for an
     *                   unknown block the upgraded entry with its swap
     * @param known      whether the game knows the shown block
     * @param properties for a known block, the kept properties by name, all accepted by the block
     */
    public record Resolved(String shown, boolean known, Map<String, String> properties) {

        public String blockId() {
            return BlockStates.id(shown);
        }
    }

    private final Upgrader upgrader;
    private final Catalogue catalogue;
    private final int gameDataVersion;

    public BlockResolution(Upgrader upgrader, Catalogue catalogue, int gameDataVersion) {
        this.upgrader = upgrader;
        this.catalogue = catalogue;
        this.gameDataVersion = gameDataVersion;
    }

    /** The entry upgraded to the game's version; as written without a data version or from the game's or a newer one. */
    public String upgraded(String raw, @Nullable Integer dataVersion) {
        if (dataVersion == null || dataVersion >= gameDataVersion) {
            return raw;
        }
        return upgrader.upgrade(raw, dataVersion);
    }

    /**
     * Resolves one palette entry.
     *
     * @param swaps block ID as written in the file to target block ID; a target the game does not know is skipped
     */
    public Resolved resolve(String raw, @Nullable Integer dataVersion, Map<String, String> swaps) {
        String upgraded = upgraded(raw, dataVersion);
        Map<String, String> properties = BlockStates.properties(upgraded);
        String target = swaps.get(BlockStates.id(raw));
        String id = target != null && catalogue.properties(target) != null ? target : BlockStates.id(upgraded);
        Map<String, Property> known = catalogue.properties(id);
        if (known == null) {
            return new Resolved(BlockStates.canonical(id, properties), false, Map.of());
        }
        TreeMap<String, String> kept = new TreeMap<>();
        properties.forEach((name, value) -> {
            Property property = known.get(name);
            if (property != null && property.accepts(value)) {
                kept.put(name, value);
            }
        });
        return new Resolved(BlockStates.canonical(id, kept), true, kept);
    }
}

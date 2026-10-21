package io.github.dirnei.mc.heavymatica.formats;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Canonical block-state strings: {@code id[key=value,...]} with keys sorted by ordinal comparison. */
public final class BlockStates {

    public static final String AIR = "minecraft:air";
    public static final String STRUCTURE_VOID = "minecraft:structure_void";

    private static final Set<String> AIR_LIKE = Set.of(AIR, "minecraft:cave_air", "minecraft:void_air", STRUCTURE_VOID);

    private BlockStates() {
    }

    public static boolean isAirLike(String blockId) {
        return AIR_LIKE.contains(blockId);
    }

    public static String id(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    public static String canonical(String id, Map<String, String> properties) {
        String namespaced = id.contains(":") ? id : "minecraft:" + id;
        if (properties.isEmpty()) {
            return namespaced;
        }
        // TreeMap with String's natural order is ordinal (UTF-16 code unit) order, like the cloud's StringComparer.Ordinal.
        TreeMap<String, String> sorted = new TreeMap<>(properties);
        StringBuilder builder = new StringBuilder(namespaced).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : sorted.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }

    /** Parses a state string such as Sponge palette keys use and returns its canonical form. */
    public static String canonical(String state) {
        int bracket = state.indexOf('[');
        if (bracket < 0) {
            return canonical(state, Map.of());
        }
        if (!state.endsWith("]")) {
            throw SchematicRejectedException.malformed("Invalid block state '" + state + "'.");
        }
        TreeMap<String, String> properties = new TreeMap<>();
        for (String part : state.substring(bracket + 1, state.length() - 1).split(",")) {
            if (part.isEmpty()) {
                continue;
            }
            int equals = part.indexOf('=');
            if (equals <= 0) {
                throw SchematicRejectedException.malformed("Invalid block state '" + state + "'.");
            }
            properties.put(part.substring(0, equals), part.substring(equals + 1));
        }
        return canonical(state.substring(0, bracket), properties);
    }

    /** Reads the {@code {Name, Properties}} compound used by structure and Litematica palettes. */
    public static String fromCompound(Nbt.Compound entry) {
        String name = entry.string("Name");
        if (name == null) {
            throw SchematicRejectedException.malformed("A palette entry has no Name.");
        }
        TreeMap<String, String> properties = new TreeMap<>();
        Nbt.Compound tag = entry.compound("Properties");
        if (tag != null) {
            for (Map.Entry<String, Nbt.Tag> property : tag.entries().entrySet()) {
                if (!(property.getValue() instanceof Nbt.Text text)) {
                    throw SchematicRejectedException.malformed("A block property is not a string.");
                }
                properties.put(property.getKey(), text.value());
            }
        }
        return canonical(name, properties);
    }

    static List<String> fromPalette(Nbt.ListTag palette) {
        return palette.items().stream().map(item -> {
            if (!(item instanceof Nbt.Compound compound)) {
                throw SchematicRejectedException.malformed("A palette entry is not a compound.");
            }
            return fromCompound(compound);
        }).toList();
    }
}

package io.github.dirnei.mc.heavymatica.blocks;

import com.mojang.serialization.Dynamic;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.blocks.BlockResolution.Resolved;
import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/** {@link BlockResolution} for the running game: Minecraft's DataFixer and block registry. Thread-safe. */
public final class BlockResolver {

    private record Upgrade(int fromDataVersion, String state) {
    }

    private static final Map<Upgrade, String> UPGRADES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, BlockResolution.Property>> PROPERTIES = new ConcurrentHashMap<>();
    // Compared by identity: marks blocks the game does not know in the cache.
    private static final Map<String, BlockResolution.Property> UNKNOWN = new HashMap<>();

    private static final int GAME_DATA_VERSION = SharedConstants.getCurrentVersion().dataVersion().version();
    private static final BlockResolution RESOLUTION = new BlockResolution(BlockResolver::upgrade, BlockResolver::properties, GAME_DATA_VERSION);

    private BlockResolver() {
    }

    public static BlockResolution resolution() {
        return RESOLUTION;
    }

    public static Resolved resolve(String raw, @Nullable Integer dataVersion, Map<String, String> swaps) {
        return RESOLUTION.resolve(raw, dataVersion, swaps);
    }

    /** The game state of a known resolved entry; null for an unknown one. */
    public static @Nullable BlockState state(Resolved resolved) {
        Block block = block(resolved.blockId());
        if (!resolved.known() || block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> property : resolved.properties().entrySet()) {
            Property<?> known = block.getStateDefinition().getProperty(property.getKey());
            if (known != null) {
                state = with(state, known, property.getValue());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static @Nullable Block block(String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    private static @Nullable Map<String, BlockResolution.Property> properties(String blockId) {
        Map<String, BlockResolution.Property> properties = PROPERTIES.computeIfAbsent(blockId, id -> {
            Block block = block(id);
            if (block == null) {
                return UNKNOWN;
            }
            Map<String, BlockResolution.Property> byName = new HashMap<>();
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                byName.put(property.getName(), value -> property.getValue(value).isPresent());
            }
            return Map.copyOf(byName);
        });
        return properties == UNKNOWN ? null : properties;
    }

    private static String upgrade(String state, int fromDataVersion) {
        return UPGRADES.computeIfAbsent(new Upgrade(fromDataVersion, state), key -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", BlockStates.id(state));
            Map<String, String> properties = BlockStates.properties(state);
            if (!properties.isEmpty()) {
                CompoundTag values = new CompoundTag();
                properties.forEach(values::putString);
                tag.put("Properties", values);
            }
            try {
                Tag fixed = DataFixers.getDataFixer()
                        .update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag), fromDataVersion, GAME_DATA_VERSION)
                        .getValue();
                if (!(fixed instanceof CompoundTag result)) {
                    return state;
                }
                TreeMap<String, String> upgraded = new TreeMap<>();
                CompoundTag values = result.getCompoundOrEmpty("Properties");
                for (String name : values.keySet()) {
                    values.getString(name).ifPresent(value -> upgraded.put(name, value));
                }
                return BlockStates.canonical(result.getStringOr("Name", BlockStates.id(state)), upgraded);
            } catch (RuntimeException e) {
                Heavymatica.LOGGER.warn("Could not upgrade block state {} from data version {}", state, fromDataVersion, e);
                return state;
            }
        });
    }
}

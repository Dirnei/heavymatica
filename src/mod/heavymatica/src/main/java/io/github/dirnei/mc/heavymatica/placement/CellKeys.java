package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.placement.BlockEquivalents.Equivalence;
import io.github.dirnei.mc.heavymatica.placement.CellState.Expected;
import io.github.dirnei.mc.heavymatica.placement.CellState.Found;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * Turns block states into the comparison's {@link Expected} and {@link Found} terms: block identity, an orientation
 * key over the placement properties only, the amount, the slab half and waterlogging. Found keys are cached per state.
 * Game thread only.
 */
final class CellKeys {

    /** Properties set by how a block is placed; anything else (connections, power, age, stair shape) is ignored. */
    static final List<Property<?>> ORIENTATION = List.of(
            BlockStateProperties.FACING,
            BlockStateProperties.HORIZONTAL_FACING,
            BlockStateProperties.FACING_HOPPER,
            BlockStateProperties.VERTICAL_DIRECTION,
            BlockStateProperties.AXIS,
            BlockStateProperties.HORIZONTAL_AXIS,
            BlockStateProperties.ROTATION_16,
            BlockStateProperties.HALF,
            BlockStateProperties.DOUBLE_BLOCK_HALF,
            BlockStateProperties.DOOR_HINGE,
            BlockStateProperties.ATTACH_FACE,
            BlockStateProperties.ORIENTATION);

    /** The properties shown to the player for a block: the orientation and the slab type. */
    static final List<Property<?>> SHOWN = Stream.concat(ORIENTATION.stream(), Stream.of(BlockStateProperties.SLAB_TYPE)).toList();

    private static final List<IntegerProperty> AMOUNTS = List.of(BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.LAYERS);

    private final Map<List<Object>, Integer> orientations = new HashMap<>();
    private final Map<BlockState, Found> found = new IdentityHashMap<>();
    private final Map<Block, Equivalence> equivalences = new IdentityHashMap<>();

    /**
     * The expected term for a schematic palette entry.
     *
     * @param raw      the entry as stored in the schematic
     * @param resolved the entry resolved, mirrored and rotated as placed; air when the game does not know the block
     */
    Expected expected(String raw, BlockState resolved) {
        String id = BlockStates.id(raw);
        if (id.equals(BlockStates.STRUCTURE_VOID)) {
            return Expected.IGNORE;
        }
        if (resolved.isAir()) {
            return BlockStates.isAirLike(id) ? Expected.AIR : Expected.IGNORE;
        }
        Equivalence equivalence = equivalence(resolved.getBlock());
        return Expected.block(block(resolved), orientation(resolved), waterlogged(resolved), ids(equivalence.same()), ids(equivalence.finishFrom()),
                equivalence.finish() == null ? -1 : equivalence.finish().ordinal()).withAmount(amount(resolved), half(resolved));
    }

    /**
     * The equivalents table's row for a planned block; the block as itself when the game does not know the block that
     * places it or that block has no item. The row's other blocks may still name blocks the game does not know.
     */
    Equivalence equivalence(Block planned) {
        return equivalences.computeIfAbsent(planned, block -> {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            Equivalence equivalence = BlockEquivalents.of(id);
            Block placedWith = known(equivalence.placedWith());
            return placedWith == null || placedWith.asItem() == Items.AIR ? Equivalence.self(id) : equivalence;
        });
    }

    static @Nullable Block known(String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    /** The sorted registry ids of the blocks the game knows. */
    private static int[] ids(List<String> blockIds) {
        return blockIds.stream().map(CellKeys::known).filter(Objects::nonNull).mapToInt(BuiltInRegistries.BLOCK::getId).sorted().distinct().toArray();
    }

    Found found(BlockState state) {
        return found.computeIfAbsent(state, key -> key.isAir() ? Found.NOTHING
                : Found.block(block(key), orientation(key), waterlogged(key), amount(key), half(key)));
    }

    /** The candles, pickles or snow layers of a state, 2 for a double slab, otherwise 1. */
    static int amount(BlockState state) {
        for (IntegerProperty property : AMOUNTS) {
            if (state.hasProperty(property)) {
                return state.getValue(property);
            }
        }
        return state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
    }

    private static int half(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            return 0;
        }
        return switch (state.getValue(BlockStateProperties.SLAB_TYPE)) {
            case BOTTOM -> 1;
            case TOP -> 2;
            case DOUBLE -> 0;
        };
    }

    private static int block(BlockState state) {
        return BuiltInRegistries.BLOCK.getId(state.getBlock());
    }

    private int orientation(BlockState state) {
        List<Object> values = new ArrayList<>();
        for (Property<?> property : ORIENTATION) {
            if (state.hasProperty(property)) {
                values.add(property);
                values.add(state.getValue(property));
            }
        }
        return orientations.computeIfAbsent(values, key -> orientations.size());
    }

    private static boolean waterlogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }
}

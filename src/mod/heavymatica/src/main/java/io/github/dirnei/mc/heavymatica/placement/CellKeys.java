package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.placement.CellState.Expected;
import io.github.dirnei.mc.heavymatica.placement.CellState.Found;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Turns block states into the comparison's {@link Expected} and {@link Found} terms: block identity, an orientation
 * key over the placement properties only, and waterlogging. Found keys are cached per state. Game thread only.
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
            BlockStateProperties.SLAB_TYPE,
            BlockStateProperties.DOOR_HINGE,
            BlockStateProperties.ATTACH_FACE,
            BlockStateProperties.ORIENTATION);

    private final Map<List<Object>, Integer> orientations = new HashMap<>();
    private final Map<BlockState, Found> found = new IdentityHashMap<>();

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
        return Expected.block(block(resolved), orientation(resolved), waterlogged(resolved));
    }

    Found found(BlockState state) {
        return found.computeIfAbsent(state, key -> key.isAir() ? Found.NOTHING : Found.block(block(key), orientation(key), waterlogged(key)));
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

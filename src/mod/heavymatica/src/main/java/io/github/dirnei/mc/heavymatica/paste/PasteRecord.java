package io.github.dirnei.mc.heavymatica.paste;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What a paste changed, for undo: the previous state and block-entity data of every changed position, in the order
 * they changed, in batches of one section each. Written and read on the server thread, except {@link #finish()}, which
 * belongs to the game thread.
 */
final class PasteRecord {

    private final ResourceKey<Level> dimension;
    private final PasteFinish finish;
    private long[] positions = new long[1024];
    private BlockState[] states = new BlockState[1024];
    private final Map<Integer, CompoundTag> blockEntities = new HashMap<>();
    private int size;
    private int[] batchEnds = new int[64];
    private int batches;

    PasteRecord(ResourceKey<Level> dimension, PasteFinish finish) {
        this.dimension = dimension;
        this.finish = finish;
    }

    PasteFinish finish() {
        return finish;
    }

    ResourceKey<Level> dimension() {
        return dimension;
    }

    void add(BlockPos pos, BlockState previous, @Nullable CompoundTag blockEntity) {
        if (size == positions.length) {
            positions = Arrays.copyOf(positions, size * 2);
            states = Arrays.copyOf(states, size * 2);
        }
        positions[size] = pos.asLong();
        states[size] = previous;
        if (blockEntity != null) {
            blockEntities.put(size, blockEntity);
        }
        size++;
    }

    /** Closes the current batch; empty batches are dropped. */
    void endBatch() {
        if (batches > 0 && batchEnds[batches - 1] == size || batches == 0 && size == 0) {
            return;
        }
        if (batches == batchEnds.length) {
            batchEnds = Arrays.copyOf(batchEnds, batches * 2);
        }
        batchEnds[batches++] = size;
    }

    /** The number of changed positions. */
    int size() {
        return size;
    }

    int batches() {
        return batches;
    }

    int batchStart(int batch) {
        return batch == 0 ? 0 : batchEnds[batch - 1];
    }

    int batchEnd(int batch) {
        return batchEnds[batch];
    }

    long position(int i) {
        return positions[i];
    }

    BlockState state(int i) {
        return states[i];
    }

    @Nullable CompoundTag blockEntity(int i) {
        return blockEntities.get(i);
    }
}

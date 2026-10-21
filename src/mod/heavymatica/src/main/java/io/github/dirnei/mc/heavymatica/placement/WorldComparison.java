package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.CellState.Expected;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * One locked placement compared with the world: what each position should hold, the {@link ComparisonState}, and
 * its GPU side (the ghost mask and the mistake boxes). Game and render thread, which are the same on the client.
 */
public final class WorldComparison implements AutoCloseable {

    private final Placement placement;
    private final Bounds bounds;
    private final GhostView view;
    private final Expected[] expected;
    private final ComparisonState state;
    private final MistakeBoxes boxes;
    private final int[] scratch = new int[2];
    private @Nullable GpuBuffer mask;

    WorldComparison(Placement placement, SchematicModel model, CellKeys keys) {
        this.placement = placement;
        this.bounds = PlacementTransform.bounds(placement);
        this.view = new GhostView(model, placement.rotation(), placement.mirror());
        List<String> palette = model.palette();
        this.expected = new Expected[palette.size()];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = keys.expected(palette.get(i), view.placed(i));
        }
        BitSet counted = new BitSet();
        int index = 0;
        for (int y = 0; y < view.sizeY(); y++) {
            for (int z = 0; z < view.sizeZ(); z++) {
                for (int x = 0; x < view.sizeX(); x++, index++) {
                    if (expected(x, y, z).counted()) {
                        counted.set(index);
                    }
                }
            }
        }
        this.state = new ComparisonState(view.sizeX(), view.sizeY(), view.sizeZ(), counted);
        this.boxes = new MistakeBoxes(state);
    }

    /** True while this comparison still describes the placement as it is. */
    boolean matches(Placement current) {
        return current.locked() && current.source().equals(placement.source()) && current.rotation() == placement.rotation()
                && current.mirror() == placement.mirror() && PlacementTransform.bounds(current).equals(bounds);
    }

    public ComparisonState state() {
        return state;
    }

    Bounds bounds() {
        return bounds;
    }

    private Expected expected(int x, int y, int z) {
        return expected[view.paletteIndex(x, y, z, scratch)];
    }

    /** The state at a world position, or null outside the placement. */
    public @Nullable CellState stateAt(BlockPos pos) {
        int x = pos.getX() - bounds.minX();
        int y = pos.getY() - bounds.minY();
        int z = pos.getZ() - bounds.minZ();
        if (x < 0 || y < 0 || z < 0 || x >= state.sizeX() || y >= state.sizeY() || z >= state.sizeZ()) {
            return null;
        }
        return state.state(state.index(x, y, z));
    }

    /** The schematic block as placed at a world position inside the placement. */
    public BlockState expectedAt(BlockPos pos) {
        return view.placed(view.paletteIndex(pos.getX() - bounds.minX(), pos.getY() - bounds.minY(), pos.getZ() - bounds.minZ(), scratch));
    }

    /** The orientation properties and waterlogging of a state, as {@code name=value} pairs, for showing to the player. */
    public static String details(BlockState state) {
        StringBuilder text = new StringBuilder();
        for (Property<?> property : CellKeys.ORIENTATION) {
            if (state.hasProperty(property)) {
                text.append(text.isEmpty() ? "" : ", ").append(property.getName()).append('=')
                        .append(state.getValue(property).toString().toLowerCase(Locale.ROOT));
            }
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            text.append(text.isEmpty() ? "" : ", ").append("waterlogged");
        }
        return text.toString();
    }

    /** Compares one local position with the block the world holds there. */
    void compare(int x, int y, int z, BlockState world, CellKeys keys) {
        state.set(x, y, z, CellState.classify(expected(x, y, z), keys.found(world)));
    }

    /**
     * Compares every position of a section that lies in a loaded chunk; positions in unloaded chunks become unknown.
     * Returns the number of positions looked at.
     */
    int compareSection(int section, ChunkReader chunks, CellKeys keys) {
        int[] min = state.sectionMin(section);
        int maxX = Math.min(min[0] + ComparisonState.SECTION, state.sizeX());
        int maxY = Math.min(min[1] + ComparisonState.SECTION, state.sizeY());
        int maxZ = Math.min(min[2] + ComparisonState.SECTION, state.sizeZ());
        boolean loaded = true;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = min[2]; z < maxZ; z++) {
            for (int x = min[0]; x < maxX; x++) {
                LevelChunk chunk = chunks.chunk(bounds.minX() + x, bounds.minZ() + z);
                for (int y = min[1]; y < maxY; y++) {
                    if (chunk == null) {
                        state.set(x, y, z, expected(x, y, z).kind() == Expected.Kind.IGNORED ? CellState.IGNORED : CellState.UNKNOWN);
                    } else {
                        compare(x, y, z, chunk.getBlockState(pos.set(bounds.minX() + x, bounds.minY() + y, bounds.minZ() + z)), keys);
                    }
                }
                loaded &= chunk != null;
            }
        }
        state.compared(section, loaded);
        return (maxX - min[0]) * (maxY - min[1]) * (maxZ - min[2]);
    }

    /** The ghost mask on the GPU, with every change since the last call uploaded. Render thread only. */
    GpuBuffer mask() {
        RenderSystem.assertOnRenderThread();
        byte[] bits = state.mask();
        if (mask == null) {
            state.takeDirtyMask();
            ByteBuffer data = direct(bits, 0, bits.length);
            try {
                mask = RenderSystem.getDevice().createBuffer(() -> "Heavymatica ghost mask", GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, data);
            } finally {
                MemoryUtil.memFree(data);
            }
            return mask;
        }
        int[] dirty = state.takeDirtyMask();
        if (dirty != null) {
            ByteBuffer data = direct(bits, dirty[0], dirty[1] - dirty[0]);
            try {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(mask.slice(dirty[0], dirty[1] - dirty[0]), data);
            } finally {
                MemoryUtil.memFree(data);
            }
        }
        return mask;
    }

    MistakeBoxes boxes() {
        return boxes;
    }

    private static ByteBuffer direct(byte[] bytes, int from, int length) {
        ByteBuffer buffer = MemoryUtil.memAlloc(length);
        buffer.put(bytes, from, length).flip();
        return buffer;
    }

    @Override
    public void close() {
        if (mask != null) {
            mask.close();
            mask = null;
        }
        boxes.close();
    }

    /** The chunk holding a world column, or null while it is not loaded. */
    interface ChunkReader {

        @Nullable LevelChunk chunk(int blockX, int blockZ);
    }
}

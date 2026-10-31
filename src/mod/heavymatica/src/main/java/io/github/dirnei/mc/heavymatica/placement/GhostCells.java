package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.dirnei.mc.heavymatica.blocks.BlockGeometry;
import io.github.dirnei.mc.heavymatica.blocks.ForwardingVertexConsumer;
import io.github.dirnei.mc.heavymatica.blocks.SpecialGeometry;
import io.github.dirnei.mc.heavymatica.blocks.TexturedOutput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * A ghost split into one buffer per block (any state) and texture, with its quads ordered by layer, so any layer range
 * of any block draws as one call per texture. Each block is meshed as if everything outside its own block type and
 * layer were air, so slices never show holes. Every vertex carries its cell index (x fastest, then z, then y) in its
 * lightmap coordinates, for {@link GhostPipeline#GHOST_MASKED}.
 */
final class GhostCells implements AutoCloseable {

    /** One block type in one texture: its buffer and where each layer's quads start; {@code layerStart[height]} is the total. */
    private record Cell(Block block, Identifier texture, GpuBuffer vertices, int[] layerStart) {
    }

    private record Key(Block block, Identifier texture) {
    }

    private final List<Cell> cells;

    private GhostCells(List<Cell> cells) {
        this.cells = cells;
    }

    /**
     * Draws the layers in {@code layers}; {@code alpha} gives each block's opacity, 0 or less to skip it, and
     * {@code mask} hides cells (see {@link ComparisonState#mask()}). Returns how many block buffers were drawn.
     */
    int draw(Matrix4fc modelView, Vector3f offset, Layers.Range layers, ToDoubleFunction<Block> alpha, GpuBuffer mask) {
        int drawn = 0;
        try (GhostDraw draw = new GhostDraw(modelView, offset, mask)) {
            for (Cell cell : cells) {
                float opacity = (float) alpha.applyAsDouble(cell.block());
                if (opacity > 0.0F) {
                    draw.quads(cell.texture(), cell.vertices(), cell.layerStart()[layers.from()], cell.layerStart()[layers.to() + 1], opacity);
                    drawn++;
                }
            }
        }
        return drawn;
    }

    @Override
    public void close() {
        cells.forEach(cell -> cell.vertices().close());
    }

    /** Uploads built geometry; render thread only. Closes the built buffers. */
    static GhostCells upload(Built built) {
        RenderSystem.assertOnRenderThread();
        try (built) {
            List<Cell> cells = new ArrayList<>();
            for (BuiltCell cell : built.cells()) {
                if (cell.mesh() != null) {
                    GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Heavymatica ghost cells", GpuBuffer.USAGE_VERTEX,
                            cell.mesh().vertexBuffer());
                    cells.add(new Cell(cell.block(), cell.texture(), buffer, cell.layerStart()));
                }
            }
            return new GhostCells(cells);
        }
    }

    /**
     * Meshes every block in one pass, y outermost, so each block's quads arrive in layer order. Worker thread; the view's
     * states must have been prepared in {@link SpecialGeometry}.
     */
    static Built build(GhostView view) {
        CellView cellView = new CellView(view);
        BlockGeometry geometry = new BlockGeometry(SpecialGeometry.get());
        Map<Key, Builder> builders = new LinkedHashMap<>();
        try {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < view.sizeY(); y++) {
                for (Builder builder : builders.values()) {
                    builder.startLayer(y);
                }
                for (int z = 0; z < view.sizeZ(); z++) {
                    for (int x = 0; x < view.sizeX(); x++) {
                        pos.set(x, y, z);
                        BlockState state = view.getBlockState(pos);
                        if (state.isAir()) {
                            continue;
                        }
                        Block block = state.getBlock();
                        cellView.cell(block, y);
                        if (hidden(cellView, pos)) {
                            continue;
                        }
                        int cell = x + view.sizeX() * (z + view.sizeZ() * y);
                        TexturedOutput output = (texture, layer) -> builders
                                .computeIfAbsent(new Key(block, texture), key -> new Builder(key, view.sizeY()))
                                .at(cell);
                        geometry.emit(cellView, pos.immutable(), state, output);
                    }
                }
            }
            List<BuiltCell> cells = new ArrayList<>();
            for (Builder builder : builders.values()) {
                cells.add(builder.finish());
            }
            return new Built(cells);
        } catch (RuntimeException | Error e) {
            builders.values().forEach(Builder::close);
            throw e;
        }
    }

    private static boolean hidden(BlockAndTintGetter view, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!view.getBlockState(pos.relative(direction)).isSolidRender()) {
                return false;
            }
        }
        return true;
    }

    /** One block type's vertices in one texture. */
    private static final class Builder {

        private final Key key;
        private final ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 12);
        private final BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        private final CellVertices vertices = new CellVertices(buffer);
        private final int[] layerStart;

        Builder(Key key, int height) {
            this.key = key;
            this.layerStart = new int[height + 1];
        }

        void startLayer(int layer) {
            layerStart[layer] = vertices.quads();
        }

        /** The buffer for the block at {@code cell}. */
        VertexConsumer at(int cell) {
            vertices.cell = cell;
            return vertices;
        }

        BuiltCell finish() {
            layerStart[layerStart.length - 1] = vertices.quads();
            return new BuiltCell(key.block(), key.texture(), bytes, buffer.build(), layerStart);
        }

        void close() {
            bytes.close();
        }
    }

    /** Writes the current cell index where the lightmap coordinates go, and counts quads. */
    private static final class CellVertices extends ForwardingVertexConsumer {

        private int cell;
        private int vertexCount;

        CellVertices(VertexConsumer delegate) {
            super(delegate);
        }

        int quads() {
            return vertexCount / 4;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
            delegate.addVertex(x, y, z, color, u, v, overlayCoords, cell, nx, ny, nz);
            vertexCount++;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            vertexCount++;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setLight(cell);
            return this;
        }
    }

    /** One block's geometry in one texture; {@code mesh} is null when nothing of it is visible. */
    record BuiltCell(Block block, Identifier texture, ByteBufferBuilder bytes, @Nullable MeshData mesh, int[] layerStart) {
    }

    /** Geometry built off the render thread, waiting for upload. */
    record Built(List<BuiltCell> cells) implements AutoCloseable {

        @Override
        public void close() {
            for (BuiltCell cell : cells) {
                if (cell.mesh() != null) {
                    cell.mesh().close();
                }
                cell.bytes().close();
            }
        }
    }

    /** The placed view, showing only one block type in one layer; everything else reads as air. */
    private static final class CellView implements BlockAndTintGetter {

        private final GhostView view;
        private Block block = Blocks.AIR;
        private int layer;

        CellView(GhostView view) {
            this.view = view;
        }

        void cell(Block block, int layer) {
            this.block = block;
            this.layer = layer;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getY() != layer) {
                return Blocks.AIR.defaultBlockState();
            }
            BlockState state = view.getBlockState(pos);
            return state.is(block) ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return view.getHeight();
        }

        @Override
        public int getMinY() {
            return 0;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return view.cardinalLighting();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return view.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer type, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening) {
            return 15;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return view.getBlockTint(pos, color);
        }
    }
}

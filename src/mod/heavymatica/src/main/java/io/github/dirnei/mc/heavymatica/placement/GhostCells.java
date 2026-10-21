package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jetbrains.annotations.Nullable;

/**
 * A ghost split into one buffer per block (any state), with its quads ordered by layer, so any layer range of any block
 * draws as one call. Each block is meshed as if everything outside its own block type and layer were air, so slices
 * never show holes. Every vertex carries its cell index (x fastest, then z, then y) in its lightmap coordinates, for
 * {@link GhostPipeline#GHOST_MASKED}.
 */
final class GhostCells implements AutoCloseable {

    /** One block type: its buffer and where each layer's quads start; {@code layerStart[height]} is the total. */
    private record Cell(Block block, GpuBuffer vertices, int[] layerStart) {
    }

    private final List<Cell> cells;

    private GhostCells(List<Cell> cells) {
        this.cells = cells;
    }

    /**
     * Draws the layers in {@code layers}; {@code alpha} gives each block's opacity, 0 or less to skip it, and
     * {@code mask} hides cells (see {@link ComparisonState#mask()}). Returns how many block types were drawn.
     */
    int draw(Matrix4fc modelView, Vector3f offset, Layers.Range layers, ToDoubleFunction<Block> alpha, GpuBuffer mask) {
        int drawn = 0;
        try (GhostDraw draw = new GhostDraw(modelView, offset, mask)) {
            for (Cell cell : cells) {
                float opacity = (float) alpha.applyAsDouble(cell.block());
                if (opacity > 0.0F) {
                    draw.quads(cell.vertices(), cell.layerStart()[layers.from()], cell.layerStart()[layers.to() + 1], opacity);
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
                    cells.add(new Cell(cell.block(), buffer, cell.layerStart()));
                }
            }
            return new GhostCells(cells);
        }
    }

    /** Meshes every block in one pass, y outermost, so each block's quads arrive in layer order. Worker thread. */
    static Built build(GhostView view) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
        ModelBlockRenderer renderer = new ModelBlockRenderer(true, true, minecraft.getBlockColors());
        CellView cellView = new CellView(view);
        Map<Block, Builder> builders = new LinkedHashMap<>();
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
                        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                            continue;
                        }
                        cellView.cell(state.getBlock(), y);
                        if (hidden(cellView, pos)) {
                            continue;
                        }
                        Builder builder = builders.computeIfAbsent(state.getBlock(), block -> new Builder(block, view.sizeX(), view.sizeY(), view.sizeZ()));
                        BlockPos fixed = pos.immutable();
                        renderer.tesselateBlock(builder::put, x, y, z, cellView, fixed, state, models.get(state), state.getSeed(fixed));
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

    private static final class Builder {

        private final Block block;
        private final ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 12);
        private final BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        private final int sizeX;
        private final int sizeZ;
        private final int[] layerStart;
        private int quads;

        Builder(Block block, int sizeX, int height, int sizeZ) {
            this.block = block;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.layerStart = new int[height + 1];
        }

        void startLayer(int layer) {
            layerStart[layer] = quads;
        }

        /** Like {@code putBakedQuad}, with the cell index written where the lightmap coordinates go. */
        void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            int cell = (int) x + sizeX * ((int) z + sizeZ * (int) y);
            Vector3f normal = quad.direction().getUnitVec3f().get(new Vector3f());
            for (int vertex = 0; vertex < 4; vertex++) {
                Vector3fc position = quad.position(vertex);
                long uv = quad.packedUV(vertex);
                int color = ARGB.multiply(instance.getColor(vertex), quad.bakedColors().color(vertex));
                buffer.addVertex(position.x() + x, position.y() + y, position.z() + z, color, UVPair.unpackU(uv), UVPair.unpackV(uv),
                        instance.overlayCoords(), cell, normal.x(), normal.y(), normal.z());
            }
            quads++;
        }

        BuiltCell finish() {
            layerStart[layerStart.length - 1] = quads;
            return new BuiltCell(block, bytes, buffer.build(), layerStart);
        }

        void close() {
            bytes.close();
        }
    }

    /** One block's built geometry; {@code mesh} is null when nothing of it is visible. */
    record BuiltCell(Block block, ByteBufferBuilder bytes, @Nullable MeshData mesh, int[] layerStart) {
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

package io.github.dirnei.mc.heavymatica.blocks;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * The geometry of one block of a schematic view, in view coordinates: its block model, the display model of a block
 * drawn by a block-entity renderer (see {@link SpecialGeometry}), its fluid, and, when none of these has anything, its
 * outline shape in its map colour. Blocks with an invisible render shape contribute only their fluid. One instance per
 * build; it may run on a worker thread once {@link SpecialGeometry#prepare} has run for the view's states.
 */
public final class BlockGeometry {

    private static final int FALLBACK_COLOR = 0xFF9A9A9A;

    private final BlockStateModelSet models;
    private final ModelBlockRenderer renderer;
    private final FluidRenderer fluids;
    private final SpecialGeometry special;
    private final float flatU;
    private final float flatV;

    private TexturedOutput output = (texture, layer) -> {
        throw new IllegalStateException("No output");
    };
    private int modelQuads;
    private final BlockQuadOutput modelOutput = (x, y, z, quad, instance) -> {
        output.buffer(TextureAtlas.LOCATION_BLOCKS, quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
        modelQuads++;
    };

    public BlockGeometry(SpecialGeometry special) {
        Minecraft minecraft = Minecraft.getInstance();
        this.models = minecraft.getModelManager().getBlockStateModelSet();
        this.renderer = new ModelBlockRenderer(true, true, minecraft.getBlockColors());
        this.fluids = new FluidRenderer(minecraft.getModelManager().getFluidStateModelSet());
        this.special = special;
        TextureAtlasSprite white = models.get(Blocks.WHITE_CONCRETE.defaultBlockState()).particleMaterial().sprite();
        this.flatU = white.getU(0.5F);
        this.flatV = white.getV(0.5F);
    }

    /** Writes the block at {@code pos} of {@code view}; returns whether it wrote anything. */
    public boolean emit(BlockAndTintGetter view, BlockPos pos, BlockState state, TexturedOutput out) {
        output = out;
        FluidState fluid = state.getFluidState();
        boolean wrote = false;
        if (state.getRenderShape() != RenderShape.INVISIBLE) {
            modelQuads = 0;
            renderer.tesselateBlock(modelOutput, pos.getX(), pos.getY(), pos.getZ(), view, pos, state, models.get(state), state.getSeed(pos));
            wrote = modelQuads > 0;
            wrote |= special.emit(state, pos, out);
            if (!wrote && fluid.isEmpty()) {
                wrote = shape(view, pos, state, out);
            }
        }
        if (!fluid.isEmpty()) {
            fluid(view, pos, state, fluid, out);
            wrote = true;
        }
        return wrote;
    }

    private void fluid(BlockAndTintGetter view, BlockPos pos, BlockState state, FluidState fluid, TexturedOutput out) {
        // FluidRenderer writes section-relative positions (pos & 15); the offset restores the view position.
        float dx = pos.getX() & ~15;
        float dy = pos.getY() & ~15;
        float dz = pos.getZ() & ~15;
        fluids.tesselate(view, pos, layer -> new OffsetVertexConsumer(out.buffer(TextureAtlas.LOCATION_BLOCKS, layer), dx, dy, dz), state, fluid);
    }

    private boolean shape(BlockAndTintGetter view, BlockPos pos, BlockState state, TexturedOutput out) {
        MapColor mapColor = state.getMapColor(view, pos);
        // The flat texel is white concrete, slightly grey; brightening keeps the map colour recognisable.
        int base = mapColor == MapColor.NONE ? FALLBACK_COLOR : ARGB.opaque(mapColor.col);
        int color = ARGB.color(255, Math.min(255, ARGB.red(base) * 6 / 5), Math.min(255, ARGB.green(base) * 6 / 5),
                Math.min(255, ARGB.blue(base) * 6 / 5));
        VertexConsumer buffer = out.buffer(TextureAtlas.LOCATION_BLOCKS, ChunkSectionLayer.CUTOUT);
        boolean[] wrote = new boolean[1];
        state.getShape(view, pos).forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            float ax = pos.getX() + (float) x0, ay = pos.getY() + (float) y0, az = pos.getZ() + (float) z0;
            float bx = pos.getX() + (float) x1, by = pos.getY() + (float) y1, bz = pos.getZ() + (float) z1;
            for (Direction face : Direction.values()) {
                int shaded = ARGB.scaleRGB(color, view.cardinalLighting().byFace(face));
                float[] c = corners(face, ax, ay, az, bx, by, bz);
                for (int i = 0; i < 4; i++) {
                    buffer.addVertex(c[i * 3], c[i * 3 + 1], c[i * 3 + 2], shaded, flatU, flatV, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT,
                            face.getStepX(), face.getStepY(), face.getStepZ());
                }
            }
            wrote[0] = true;
        });
        return wrote[0];
    }

    /** A face's corners in vanilla's winding order. */
    private static float[] corners(Direction face, float ax, float ay, float az, float bx, float by, float bz) {
        return switch (face) {
            case DOWN -> new float[] {ax, ay, bz, ax, ay, az, bx, ay, az, bx, ay, bz};
            case UP -> new float[] {ax, by, az, ax, by, bz, bx, by, bz, bx, by, az};
            case NORTH -> new float[] {bx, by, az, bx, ay, az, ax, ay, az, ax, by, az};
            case SOUTH -> new float[] {ax, by, bz, ax, ay, bz, bx, ay, bz, bx, by, bz};
            case WEST -> new float[] {ax, by, az, ax, ay, az, ax, ay, bz, ax, by, bz};
            case EAST -> new float[] {bx, by, bz, bx, ay, bz, bx, ay, az, bx, by, az};
        };
    }

    /** Moves every vertex by a fixed offset. */
    private static final class OffsetVertexConsumer extends ForwardingVertexConsumer {

        private final float dx;
        private final float dy;
        private final float dz;

        OffsetVertexConsumer(VertexConsumer delegate, float dx, float dy, float dz) {
            super(delegate);
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
            delegate.addVertex(x + dx, y + dy, z + dz, color, u, v, overlayCoords, lightCoords, nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x + dx, y + dy, z + dz);
            return this;
        }
    }
}

package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tessellates a ghost off the render thread (D6): every block of a view of size {@code sx x sy x sz} that is not air,
 * has a model and is not fully hidden by full opaque neighbours. Like vanilla section compiling, this only reads models
 * and may run on a worker thread.
 */
final class GhostMeshBuilder {

    private GhostMeshBuilder() {
    }

    static GhostMesh.Built build(BlockAndTintGetter view, int sx, int sy, int sz) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
        ModelBlockRenderer renderer = new ModelBlockRenderer(true, true, minecraft.getBlockColors());
        ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 16);
        try {
            BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            PoseStack pose = new PoseStack();
            BlockQuadOutput output = (x, y, z, quad, instance) -> {
                pose.pushPose();
                pose.translate(x, y, z);
                buffer.putBakedQuad(pose.last(), quad, instance);
                pose.popPose();
            };
            int blocks = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        pos.set(x, y, z);
                        BlockState state = view.getBlockState(pos);
                        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || hidden(view, pos, sx, sy, sz)) {
                            continue;
                        }
                        BlockPos fixed = pos.immutable();
                        renderer.tesselateBlock(output, x, y, z, view, fixed, state, models.get(state), state.getSeed(fixed));
                        blocks++;
                    }
                }
            }
            return new GhostMesh.Built(bytes, buffer.build(), blocks);
        } catch (RuntimeException | Error e) {
            bytes.close();
            throw e;
        }
    }

    /** A block surrounded by full opaque blocks on every side cannot be seen. */
    private static boolean hidden(BlockAndTintGetter view, BlockPos pos, int sx, int sy, int sz) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            boolean inside = neighbour.getX() >= 0 && neighbour.getY() >= 0 && neighbour.getZ() >= 0
                    && neighbour.getX() < sx && neighbour.getY() < sy && neighbour.getZ() < sz;
            if (!inside || !view.getBlockState(neighbour).isSolidRender()) {
                return false;
            }
        }
        return true;
    }
}

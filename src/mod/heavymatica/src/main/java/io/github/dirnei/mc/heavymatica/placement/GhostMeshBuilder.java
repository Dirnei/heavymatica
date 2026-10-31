package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.dirnei.mc.heavymatica.blocks.BlockGeometry;
import io.github.dirnei.mc.heavymatica.blocks.SpecialGeometry;
import io.github.dirnei.mc.heavymatica.blocks.TexturedOutput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tessellates a ghost off the render thread, one buffer per texture: every block of a view of size
 * {@code sx x sy x sz} that is not air, has geometry and is not fully hidden by full opaque neighbours. The view's
 * states must have been prepared in {@link SpecialGeometry}.
 */
final class GhostMeshBuilder {

    private GhostMeshBuilder() {
    }

    static GhostMesh.Built build(BlockAndTintGetter view, int sx, int sy, int sz) {
        Map<Identifier, BuiltBuffer> buffers = new LinkedHashMap<>();
        try {
            TexturedOutput output = (texture, layer) -> buffers.computeIfAbsent(texture, BuiltBuffer::new).buffer;
            BlockGeometry geometry = new BlockGeometry(SpecialGeometry.get());
            int blocks = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        pos.set(x, y, z);
                        BlockState state = view.getBlockState(pos);
                        if (state.isAir() || hidden(view, pos, sx, sy, sz)) {
                            continue;
                        }
                        if (geometry.emit(view, pos.immutable(), state, output)) {
                            blocks++;
                        }
                    }
                }
            }
            List<GhostMesh.BuiltTexture> textures = new ArrayList<>();
            for (BuiltBuffer buffer : buffers.values()) {
                textures.add(new GhostMesh.BuiltTexture(buffer.texture, buffer.bytes, buffer.buffer.build()));
            }
            return new GhostMesh.Built(textures, blocks);
        } catch (RuntimeException | Error e) {
            buffers.values().forEach(buffer -> buffer.bytes.close());
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

    private static final class BuiltBuffer {

        private final Identifier texture;
        private final ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 16);
        private final BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

        BuiltBuffer(Identifier texture) {
            this.texture = texture;
        }
    }
}

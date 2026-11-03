package io.github.dirnei.mc.heavymatica.blocks;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The geometry of blocks that block-entity renderers draw (chests, beds, signs, banners, heads, ...), taken from
 * vanilla's block display models and baked once per block state into block-local vertices per texture. Baking reads
 * models that item rendering shares and animates, so it runs on the render thread; finished entries are immutable and
 * may be read from any thread. Entries are dropped when the game's model set changes.
 */
public final class SpecialGeometry {

    private static final SpecialGeometry INSTANCE = new SpecialGeometry();

    /** One texture's vertices: position, uv and normal as 8 floats per vertex, and a colour each. */
    record Part(Identifier texture, ChunkSectionLayer layer, float[] vertices, int[] colors) {

        int vertexCount() {
            return colors.length;
        }
    }

    record Baked(List<Part> parts) {

        static final Baked EMPTY = new Baked(List.of());
    }

    private final Map<BlockState, Baked> baked = new ConcurrentHashMap<>();
    private @Nullable Object modelSet;

    private SpecialGeometry() {
    }

    public static SpecialGeometry get() {
        return INSTANCE;
    }

    /** Bakes the display models of the states not baked yet. Render thread only. */
    public void prepare(Iterable<BlockState> states) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        Object current = minecraft.getModelManager().getBlockModelSet();
        if (current != modelSet) {
            baked.clear();
            modelSet = current;
        }
        BlockModelResolver resolver = null;
        for (BlockState state : states) {
            if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE || baked.containsKey(state)) {
                continue;
            }
            if (resolver == null) {
                resolver = new BlockModelResolver(minecraft.getModelManager());
            }
            baked.put(state, bake(resolver, state));
        }
    }

    /** The baked geometry of a state; empty when it has none or was not prepared. */
    Baked baked(BlockState state) {
        return baked.getOrDefault(state, Baked.EMPTY);
    }

    /** Each texture and layer of a state's baked geometry with its vertex count and extent, for dev checks. */
    public List<String> summary(BlockState state) {
        List<String> lines = new ArrayList<>();
        for (Part part : baked(state).parts()) {
            float[] v = part.vertices();
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int i = 0; i < part.vertexCount(); i++) {
                for (int axis = 0; axis < 3; axis++) {
                    min[axis] = Math.min(min[axis], v[i * 8 + axis]);
                    max[axis] = Math.max(max[axis], v[i * 8 + axis]);
                }
            }
            lines.add(String.format(java.util.Locale.ROOT, "%s %s: %d vertices, %.2f,%.2f,%.2f to %.2f,%.2f,%.2f", part.texture(), part.layer(),
                    part.vertexCount(), min[0], min[1], min[2], max[0], max[1], max[2]));
        }
        return lines;
    }

    /** Writes a state's geometry at {@code pos}; returns whether it had any. */
    boolean emit(BlockState state, BlockPos pos, TexturedOutput out) {
        List<Part> parts = baked(state).parts();
        for (Part part : parts) {
            VertexConsumer buffer = out.buffer(part.texture(), part.layer());
            float[] v = part.vertices();
            for (int i = 0; i < part.vertexCount(); i++) {
                int at = i * 8;
                buffer.addVertex(v[at] + pos.getX(), v[at + 1] + pos.getY(), v[at + 2] + pos.getZ(), part.colors()[i], v[at + 3], v[at + 4],
                        OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, v[at + 5], v[at + 6], v[at + 7]);
            }
        }
        return !parts.isEmpty();
    }

    private static Baked bake(BlockModelResolver resolver, BlockState state) {
        BlockModelRenderState renderState = new BlockModelRenderState();
        resolver.update(renderState, state, BlockDisplayContext.create());
        CapturingSubmits capture = new CapturingSubmits();
        renderState.submit(new PoseStack(), capture, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        return capture.baked();
    }

    /** Collects the vertices of one texture and layer. */
    static final class PartBuilder implements VertexConsumer {

        private final Identifier texture;
        private final ChunkSectionLayer layer;
        private float[] vertices = new float[8 * 64];
        private int[] colors = new int[64];
        private int count;
        private boolean open;

        PartBuilder(Identifier texture, ChunkSectionLayer layer) {
            this.texture = texture;
            this.layer = layer;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
            addVertex(x, y, z);
            setColor(color);
            setUv(u, v);
            setNormal(nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (count == colors.length) {
                vertices = java.util.Arrays.copyOf(vertices, vertices.length * 2);
                colors = java.util.Arrays.copyOf(colors, colors.length * 2);
            }
            int at = count * 8;
            vertices[at] = x;
            vertices[at + 1] = y;
            vertices[at + 2] = z;
            colors[count] = -1;
            count++;
            open = true;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255);
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (open) {
                colors[count - 1] = color;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (open) {
                vertices[(count - 1) * 8 + 3] = u;
                vertices[(count - 1) * 8 + 4] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (open) {
                int at = (count - 1) * 8;
                vertices[at + 5] = x;
                vertices[at + 6] = y;
                vertices[at + 7] = z;
            }
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Nullable Part build() {
            return count == 0 ? null : new Part(texture, layer, java.util.Arrays.copyOf(vertices, count * 8), java.util.Arrays.copyOf(colors, count));
        }
    }

    /** Builders per texture and layer, in first-use order. */
    static final class Parts {

        private record Key(Identifier texture, ChunkSectionLayer layer) {
        }

        private final Map<Key, PartBuilder> builders = new LinkedHashMap<>();

        PartBuilder of(Identifier texture, ChunkSectionLayer layer) {
            return builders.computeIfAbsent(new Key(texture, layer), key -> new PartBuilder(key.texture(), key.layer()));
        }

        Baked baked() {
            List<Part> parts = new ArrayList<>();
            for (PartBuilder builder : builders.values()) {
                Part part = builder.build();
                if (part != null) {
                    parts.add(part);
                }
            }
            return parts.isEmpty() ? Baked.EMPTY : new Baked(List.copyOf(parts));
        }
    }
}

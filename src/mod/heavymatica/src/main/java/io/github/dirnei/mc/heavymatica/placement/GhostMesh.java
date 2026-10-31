package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * A ghost's block geometry on the GPU, in placement-local coordinates, one buffer per texture: drawn every frame with
 * the placement's position as model offset, so moving a placement never rebuilds it.
 */
public final class GhostMesh implements AutoCloseable {

    private record Range(Identifier texture, GpuBuffer vertices, int quads) {
    }

    private final List<Range> ranges;
    private final int blocks;

    private GhostMesh(List<Range> ranges, int blocks) {
        this.ranges = ranges;
        this.blocks = blocks;
    }

    /** Uploads built geometry; render thread only. Closes the built buffers. */
    static GhostMesh upload(Built built) {
        RenderSystem.assertOnRenderThread();
        try (built) {
            List<Range> ranges = new ArrayList<>();
            for (BuiltTexture texture : built.textures()) {
                if (texture.mesh() != null) {
                    GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Heavymatica ghost", GpuBuffer.USAGE_VERTEX,
                            texture.mesh().vertexBuffer());
                    ranges.add(new Range(texture.texture(), buffer, texture.mesh().drawState().indexCount() / 6));
                }
            }
            return new GhostMesh(ranges, built.blocks());
        }
    }

    /** Visible blocks in this mesh, counted against the rendering budget. */
    public int blocks() {
        return blocks;
    }

    /** Draws at {@code offset} (placement minimum corner minus camera position). Render thread only. */
    public void draw(Matrix4fc modelView, Vector3f offset, float alpha) {
        if (ranges.isEmpty()) {
            return;
        }
        try (GhostDraw draw = new GhostDraw(modelView, offset)) {
            for (Range range : ranges) {
                draw.quads(range.texture(), range.vertices(), 0, range.quads(), alpha);
            }
        }
    }

    @Override
    public void close() {
        ranges.forEach(range -> range.vertices().close());
    }

    /** One texture's geometry built off the render thread; {@code mesh} is null when it has nothing. */
    record BuiltTexture(Identifier texture, ByteBufferBuilder bytes, @Nullable MeshData mesh) {
    }

    /** Geometry built off the render thread, waiting for upload. */
    record Built(List<BuiltTexture> textures, int blocks) implements AutoCloseable {

        @Override
        public void close() {
            for (BuiltTexture texture : textures) {
                if (texture.mesh() != null) {
                    texture.mesh().close();
                }
                texture.bytes().close();
            }
        }
    }
}

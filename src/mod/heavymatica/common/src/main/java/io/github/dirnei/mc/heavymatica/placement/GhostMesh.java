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
    private final @Nullable Range placeholders;
    private final int blocks;

    private GhostMesh(List<Range> ranges, @Nullable Range placeholders, int blocks) {
        this.ranges = ranges;
        this.placeholders = placeholders;
        this.blocks = blocks;
    }

    /** Uploads built geometry; render thread only. Closes the built buffers. */
    static GhostMesh upload(Built built) {
        RenderSystem.assertOnRenderThread();
        try (built) {
            List<Range> ranges = new ArrayList<>();
            for (BuiltTexture texture : built.textures()) {
                Range range = range(texture);
                if (range != null) {
                    ranges.add(range);
                }
            }
            return new GhostMesh(ranges, built.placeholders() == null ? null : range(built.placeholders()), built.blocks());
        }
    }

    private static @Nullable Range range(BuiltTexture texture) {
        if (texture.mesh() == null) {
            return null;
        }
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Heavymatica ghost", GpuBuffer.USAGE_VERTEX, texture.mesh().vertexBuffer());
        return new Range(texture.texture(), buffer, texture.mesh().drawState().indexCount() / 6);
    }

    /** Visible blocks in this mesh, counted against the rendering budget. */
    public int blocks() {
        return blocks;
    }

    /**
     * Draws at {@code offset} (placement minimum corner minus camera position), the unknown blocks' placeholders at
     * {@code placeholderAlpha}, 0 or less to skip them. Render thread only.
     */
    public void draw(Matrix4fc modelView, Vector3f offset, float alpha, float placeholderAlpha) {
        boolean drawPlaceholders = placeholders != null && placeholderAlpha > 0.0F;
        if (ranges.isEmpty() && !drawPlaceholders) {
            return;
        }
        try (GhostDraw draw = new GhostDraw(modelView, offset)) {
            for (Range range : ranges) {
                draw.quads(range.texture(), range.vertices(), 0, range.quads(), alpha);
            }
            if (drawPlaceholders) {
                draw.quads(placeholders.texture(), placeholders.vertices(), 0, placeholders.quads(), placeholderAlpha);
            }
        }
    }

    @Override
    public void close() {
        ranges.forEach(range -> range.vertices().close());
        if (placeholders != null) {
            placeholders.vertices().close();
        }
    }

    /** One texture's geometry built off the render thread; {@code mesh} is null when it has nothing. */
    record BuiltTexture(Identifier texture, ByteBufferBuilder bytes, @Nullable MeshData mesh) {

        void close() {
            if (mesh != null) {
                mesh.close();
            }
            bytes.close();
        }
    }

    /** Geometry built off the render thread, waiting for upload; {@code placeholders} is null without unknown blocks. */
    record Built(List<BuiltTexture> textures, @Nullable BuiltTexture placeholders, int blocks) implements AutoCloseable {

        @Override
        public void close() {
            for (BuiltTexture texture : textures) {
                texture.close();
            }
            if (placeholders != null) {
                placeholders.close();
            }
        }
    }
}

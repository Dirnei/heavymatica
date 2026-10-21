package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * A ghost's block geometry on the GPU, in placement-local coordinates: drawn every frame with the placement's
 * position as model offset, so moving a placement never rebuilds it.
 */
public final class GhostMesh implements AutoCloseable {

    private final @Nullable GpuBuffer vertices;
    private final int indexCount;
    private final int blocks;

    private GhostMesh(@Nullable GpuBuffer vertices, int indexCount, int blocks) {
        this.vertices = vertices;
        this.indexCount = indexCount;
        this.blocks = blocks;
    }

    /** Uploads built geometry; render thread only. Closes the built buffers. */
    static GhostMesh upload(Built built) {
        RenderSystem.assertOnRenderThread();
        try (built) {
            if (built.mesh() == null) {
                return new GhostMesh(null, 0, built.blocks());
            }
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Heavymatica ghost", GpuBuffer.USAGE_VERTEX, built.mesh().vertexBuffer());
            return new GhostMesh(buffer, built.mesh().drawState().indexCount(), built.blocks());
        }
    }

    /** Visible blocks in this mesh, counted against the rendering budget. */
    public int blocks() {
        return blocks;
    }

    /** Draws at {@code offset} (placement minimum corner minus camera position). Render thread only. */
    public void draw(Matrix4fc modelView, Vector3f offset, float alpha) {
        if (vertices == null || indexCount == 0) {
            return;
        }
        try (GhostDraw draw = new GhostDraw(modelView, offset)) {
            draw.quads(vertices, 0, indexCount / 6, alpha);
        }
    }

    @Override
    public void close() {
        if (vertices != null) {
            vertices.close();
        }
    }

    /** Geometry built off the render thread, waiting for upload. {@code mesh} is null when nothing is visible. */
    record Built(ByteBufferBuilder bytes, @Nullable MeshData mesh, int blocks) implements AutoCloseable {

        @Override
        public void close() {
            if (mesh != null) {
                mesh.close();
            }
            bytes.close();
        }
    }
}

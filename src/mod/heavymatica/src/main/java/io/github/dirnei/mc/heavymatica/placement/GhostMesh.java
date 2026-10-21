package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

/**
 * A ghost's block geometry on the GPU (D6), in placement-local coordinates: drawn every frame with the placement's
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
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, alpha), offset, new Matrix4f());
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Heavymatica ghost",
                target.getColorTextureView(), OptionalInt.empty(), target.useDepth ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            pass.setPipeline(GhostPipeline.GHOST);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView(),
                    RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true));
            pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(0, 0, indexCount, 1);
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

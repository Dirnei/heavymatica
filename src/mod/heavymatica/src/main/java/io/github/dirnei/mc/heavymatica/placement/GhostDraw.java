package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
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
 * Ghost quads of {@link GhostPipeline#GHOST}, or of {@link GhostPipeline#GHOST_MASKED} when a mask is given, each draw
 * with its own opacity, sent as one render pass on close: depth for every draw first, then colour.
 * Uniforms and the index buffer are written before the pass opens, since nothing may be written while it is open.
 * Render thread only.
 */
final class GhostDraw implements AutoCloseable {

    private static final int INDICES_PER_QUAD = 6;

    private record Quads(GpuBuffer vertices, int firstQuad, int endQuad, float alpha) {
    }

    private final Matrix4fc modelView;
    private final Vector3f offset;
    private final @Nullable GpuBuffer mask;
    private final List<Quads> draws = new ArrayList<>();

    /** @param offset placement minimum corner minus camera position */
    GhostDraw(Matrix4fc modelView, Vector3f offset) {
        this(modelView, offset, null);
    }

    /** @param mask one bit per cell, see {@link ComparisonState#mask()}; null to draw every cell */
    GhostDraw(Matrix4fc modelView, Vector3f offset, @Nullable GpuBuffer mask) {
        this.modelView = modelView;
        this.offset = offset;
        this.mask = mask;
    }

    /** Queues quads {@code firstQuad} up to (excluding) {@code endQuad} of a vertex buffer. */
    void quads(GpuBuffer vertices, int firstQuad, int endQuad, float alpha) {
        if (endQuad > firstQuad) {
            draws.add(new Quads(vertices, firstQuad, endQuad, alpha));
        }
    }

    @Override
    public void close() {
        if (draws.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        List<GpuBufferSlice> transforms = new ArrayList<>();
        int maxQuad = 0;
        for (Quads draw : draws) {
            transforms.add(RenderSystem.getDynamicUniforms()
                    .writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, draw.alpha()), offset, new Matrix4f()));
            maxQuad = Math.max(maxQuad, draw.endQuad());
        }
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(maxQuad * INDICES_PER_QUAD);
        RenderTarget target = minecraft.getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Heavymatica ghost",
                target.getColorTextureView(), OptionalInt.empty(), target.useDepth ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            drawAll(pass, mask == null ? GhostPipeline.GHOST_DEPTH : GhostPipeline.GHOST_MASKED_DEPTH, transforms, indices, indexBuffer);
            drawAll(pass, mask == null ? GhostPipeline.GHOST : GhostPipeline.GHOST_MASKED, transforms, indices, indexBuffer);
        }
    }

    private void drawAll(RenderPass pass, RenderPipeline pipeline, List<GpuBufferSlice> transforms, RenderSystem.AutoStorageIndexBuffer indices,
            GpuBuffer indexBuffer) {
        Minecraft minecraft = Minecraft.getInstance();
        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        if (mask != null) {
            pass.setUniform("GhostMask", mask.slice());
        }
        pass.bindTexture("Sampler0", minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView(),
                RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true));
        pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        pass.setIndexBuffer(indexBuffer, indices.type());
        for (int i = 0; i < draws.size(); i++) {
            Quads draw = draws.get(i);
            pass.setUniform("DynamicTransforms", transforms.get(i));
            pass.setVertexBuffer(0, draw.vertices());
            pass.drawIndexed(0, draw.firstQuad() * INDICES_PER_QUAD, (draw.endQuad() - draw.firstQuad()) * INDICES_PER_QUAD, 1);
        }
    }
}

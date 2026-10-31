package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

/**
 * Ghost quads of {@link GhostPipeline#GHOST}, or of {@link GhostPipeline#GHOST_MASKED} when a mask is given, each draw
 * with its own texture and opacity, sent as one render pass on close: depth for every draw first, then colour. Draws
 * are grouped by texture, so each texture is bound once per pass.
 * Uniforms and the index buffer are written before the pass opens, since nothing may be written while it is open.
 * Render thread only.
 */
final class GhostDraw implements AutoCloseable {

    private static final int INDICES_PER_QUAD = 6;

    private record Quads(Identifier texture, GpuBuffer vertices, int firstQuad, int endQuad, float alpha) {
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

    /** Queues quads {@code firstQuad} up to (excluding) {@code endQuad} of a vertex buffer drawn with {@code texture}. */
    void quads(Identifier texture, GpuBuffer vertices, int firstQuad, int endQuad, float alpha) {
        if (endQuad > firstQuad) {
            draws.add(new Quads(texture, vertices, firstQuad, endQuad, alpha));
        }
    }

    @Override
    public void close() {
        if (draws.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        draws.sort(Comparator.comparing((Quads draw) -> !draw.texture().equals(TextureAtlas.LOCATION_BLOCKS)).thenComparing(draw -> draw.texture().toString()));
        List<GpuBufferSlice> transforms = new ArrayList<>();
        int maxQuad = 0;
        for (Quads draw : draws) {
            transforms.add(RenderSystem.getDynamicUniforms()
                    .writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, draw.alpha()), offset, new Matrix4f()));
            maxQuad = Math.max(maxQuad, draw.endQuad());
        }
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(maxQuad * INDICES_PER_QUAD);
        // Looking a texture up may load and upload it, which must not happen while the pass is open.
        Map<Identifier, Sampled> textures = new HashMap<>();
        for (Quads draw : draws) {
            textures.computeIfAbsent(draw.texture(), GhostDraw::sampled);
        }
        RenderTarget target = minecraft.getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Heavymatica ghost",
                target.getColorTextureView(), OptionalInt.empty(), target.useDepth ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            drawAll(pass, mask == null ? GhostPipeline.GHOST_DEPTH : GhostPipeline.GHOST_MASKED_DEPTH, transforms, textures, indices, indexBuffer);
            drawAll(pass, mask == null ? GhostPipeline.GHOST : GhostPipeline.GHOST_MASKED, transforms, textures, indices, indexBuffer);
        }
    }

    private void drawAll(RenderPass pass, RenderPipeline pipeline, List<GpuBufferSlice> transforms, Map<Identifier, Sampled> textures,
            RenderSystem.AutoStorageIndexBuffer indices, GpuBuffer indexBuffer) {
        Minecraft minecraft = Minecraft.getInstance();
        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        if (mask != null) {
            pass.setUniform("GhostMask", mask.slice());
        }
        pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        pass.setIndexBuffer(indexBuffer, indices.type());
        Identifier bound = null;
        for (int i = 0; i < draws.size(); i++) {
            Quads draw = draws.get(i);
            if (!draw.texture().equals(bound)) {
                Sampled texture = textures.get(draw.texture());
                pass.bindTexture("Sampler0", texture.view(), texture.sampler());
                bound = draw.texture();
            }
            pass.setUniform("DynamicTransforms", transforms.get(i));
            pass.setVertexBuffer(0, draw.vertices());
            pass.drawIndexed(0, draw.firstQuad() * INDICES_PER_QUAD, (draw.endQuad() - draw.firstQuad()) * INDICES_PER_QUAD, 1);
        }
    }

    private record Sampled(GpuTextureView view, GpuSampler sampler) {
    }

    /** The block atlas keeps the mipmapped sampler of block rendering; other textures use their own. */
    private static Sampled sampled(Identifier texture) {
        AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
        GpuSampler sampler = texture.equals(TextureAtlas.LOCATION_BLOCKS)
                ? RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true)
                : loaded.getSampler();
        return new Sampled(loaded.getTextureView(), sampler);
    }
}

package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.textures.TextureFormat;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Vanilla's block shader with translucent blending, depth test on and depth write off, so
 * real blocks hide ghosts and ghosts never hide each other. The alpha comes from the colour modulator. The masked
 * variant reads each vertex's cell index from its lightmap coordinates and drops cells whose bit in
 * {@code GhostMask} is clear. Mistake boxes are plain coloured quads with the same depth settings.
 */
public final class GhostPipeline {

    public static final RenderPipeline GHOST = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "pipeline/ghost"))
            .withShaderDefine("ALPHA_CUTOUT", 0.01F)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    public static final RenderPipeline GHOST_MASKED = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "pipeline/ghost_masked"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "core/ghost_masked"))
            .withUniform("GhostMask", UniformType.TEXEL_BUFFER, TextureFormat.RED8I)
            .withShaderDefine("ALPHA_CUTOUT", 0.01F)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    public static final RenderPipeline MISTAKE_BOX = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "pipeline/mistake_box"))
            .withCull(false)
            .build();

    private GhostPipeline() {
    }

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GHOST);
        event.registerPipeline(GHOST_MASKED);
        event.registerPipeline(MISTAKE_BOX);
    }
}

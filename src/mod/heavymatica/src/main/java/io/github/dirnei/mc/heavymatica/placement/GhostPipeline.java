package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import io.github.dirnei.mc.heavymatica.Heavymatica;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * Vanilla's block shader with translucent blending, depth test on and depth write off, so
 * real blocks hide ghosts and ghosts never hide each other. The alpha comes from the colour modulator.
 */
public final class GhostPipeline {

    public static final RenderPipeline GHOST = RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "pipeline/ghost"))
            .withShaderDefine("ALPHA_CUTOUT", 0.01F)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    private GhostPipeline() {
    }

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GHOST);
    }
}

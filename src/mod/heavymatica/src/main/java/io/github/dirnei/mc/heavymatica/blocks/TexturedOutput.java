package io.github.dirnei.mc.heavymatica.blocks;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;

/** Where block geometry goes: one buffer per texture (an atlas or a standalone texture) and layer. */
@FunctionalInterface
public interface TexturedOutput {

    VertexConsumer buffer(Identifier texture, ChunkSectionLayer layer);
}

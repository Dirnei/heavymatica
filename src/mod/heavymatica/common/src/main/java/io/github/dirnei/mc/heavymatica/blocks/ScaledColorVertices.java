package io.github.dirnei.mc.heavymatica.blocks;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;

/** Scales the RGB of every vertex colour by a factor, leaving alpha alone. */
final class ScaledColorVertices extends ForwardingVertexConsumer {

    private final float factor;

    ScaledColorVertices(VertexConsumer delegate, float factor) {
        super(delegate);
        this.factor = factor;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
        delegate.addVertex(x, y, z, ARGB.scaleRGB(color, factor), u, v, overlayCoords, lightCoords, nx, ny, nz);
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(ARGB.scaleRGB(ARGB.color(a, r, g, b), factor));
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        delegate.setColor(ARGB.scaleRGB(color, factor));
        return this;
    }
}

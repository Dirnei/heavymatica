package io.github.dirnei.mc.heavymatica.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/** Transforms every vertex position and normal by a pose. */
public final class PoseVertexConsumer extends ForwardingVertexConsumer {

    private final PoseStack.Pose pose;
    private final Vector3f scratch = new Vector3f();

    public PoseVertexConsumer(VertexConsumer delegate, PoseStack.Pose pose) {
        super(delegate);
        this.pose = pose;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
        pose.pose().transformPosition(x, y, z, scratch);
        float px = scratch.x();
        float py = scratch.y();
        float pz = scratch.z();
        pose.transformNormal(nx, ny, nz, scratch);
        delegate.addVertex(px, py, pz, color, u, v, overlayCoords, lightCoords, scratch.x(), scratch.y(), scratch.z());
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        pose.pose().transformPosition(x, y, z, scratch);
        delegate.addVertex(scratch.x(), scratch.y(), scratch.z());
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        pose.transformNormal(x, y, z, scratch);
        delegate.setNormal(scratch.x(), scratch.y(), scratch.z());
        return this;
    }
}

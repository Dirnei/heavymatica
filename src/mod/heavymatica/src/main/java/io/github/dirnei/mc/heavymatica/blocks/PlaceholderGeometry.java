package io.github.dirnei.mc.heavymatica.blocks;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.CardinalLighting;

/**
 * A full cube with the game's missing texture, standing in for a block the game does not know. Writes to the block
 * atlas. May run on a worker thread.
 */
public final class PlaceholderGeometry {

    private final float u0;
    private final float u1;
    private final float v0;
    private final float v1;

    public PlaceholderGeometry() {
        TextureAtlasSprite missing = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).missingSprite();
        this.u0 = missing.getU0();
        this.u1 = missing.getU1();
        this.v0 = missing.getV0();
        this.v1 = missing.getV1();
    }

    /** Writes the cube at {@code x, y, z}, leaving out the faces {@code culled} names; returns whether it wrote any. */
    public boolean emit(VertexConsumer out, int x, int y, int z, Predicate<Direction> culled) {
        boolean wrote = false;
        for (Direction face : Direction.values()) {
            if (culled.test(face)) {
                continue;
            }
            int color = ARGB.scaleRGB(0xFFFFFFFF, FaceShade.of(CardinalLighting.DEFAULT, face));
            float[] c = BlockGeometry.corners(face, x, y, z, x + 1, y + 1, z + 1);
            float[] u = {u0, u0, u1, u1};
            float[] v = {v0, v1, v1, v0};
            for (int i = 0; i < 4; i++) {
                out.addVertex(c[i * 3], c[i * 3 + 1], c[i * 3 + 2], color, u[i], v[i], OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT,
                        face.getStepX(), face.getStepY(), face.getStepZ());
            }
            wrote = true;
        }
        return wrote;
    }
}

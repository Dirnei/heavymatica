package io.github.dirnei.mc.heavymatica.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Draws the capture box: a marker on each set corner, the active one brighter, and the outline both corners span. */
public final class CaptureRenderer {

    private static final int OUTLINE = 0xFF80FF80;
    private static final int CORNER = 0xFF4080FF;
    private static final int ACTIVE_CORNER = 0xFFFFFF40;
    /** Lines sit this far outside the blocks, so they don't flicker against block faces. */
    private static final double INFLATE = 0.005;

    private final CaptureSession session;

    public CaptureRenderer(CaptureSession session) {
        this.session = session;
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterTranslucentBlocks event) -> render(event));
    }

    private void render(RenderLevelStageEvent event) {
        CaptureBox box = session.box().orElse(null);
        Minecraft minecraft = Minecraft.getInstance();
        if (box == null || minecraft.level == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        float lineWidth = minecraft.gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
        box.bounds().ifPresent(bounds -> cube(pose, lines, camera, bounds.minX(), bounds.minY(), bounds.minZ(), bounds.width(), bounds.height(),
                bounds.length(), OUTLINE, lineWidth * 2.0F));
        box.first().ifPresent(corner -> cube(pose, lines, camera, corner.x(), corner.y(), corner.z(), 1, 1, 1,
                box.secondActive() ? CORNER : ACTIVE_CORNER, box.secondActive() ? lineWidth : lineWidth * 3.0F));
        box.second().ifPresent(corner -> cube(pose, lines, camera, corner.x(), corner.y(), corner.z(), 1, 1, 1,
                box.secondActive() ? ACTIVE_CORNER : CORNER, box.secondActive() ? lineWidth * 3.0F : lineWidth));
        buffers.endBatch(RenderTypes.lines());
    }

    private static void cube(PoseStack pose, VertexConsumer lines, Vec3 camera, int x, int y, int z, int width, int height, int length, int color,
                             float lineWidth) {
        ShapeRenderer.renderShape(pose, lines, Shapes.create(-INFLATE, -INFLATE, -INFLATE, width + INFLATE, height + INFLATE, length + INFLATE),
                x - camera.x, y - camera.y, z - camera.z, color, lineWidth);
    }
}

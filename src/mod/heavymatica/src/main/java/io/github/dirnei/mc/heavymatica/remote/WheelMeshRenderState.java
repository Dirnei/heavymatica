package io.github.dirnei.mc.heavymatica.remote;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jetbrains.annotations.Nullable;

/** A {@link WheelMesh} handed to the GUI renderer as plain coloured quads. */
public record WheelMeshRenderState(Matrix3x2fc pose, float[] positions, int[] colors, @Nullable ScreenRectangle scissorArea,
                                   @Nullable ScreenRectangle bounds) implements GuiElementRenderState {

    /** Submits the mesh under the current pose; an empty mesh submits nothing. */
    public static void submit(GuiGraphicsExtractor graphics, WheelMesh mesh) {
        float[] b = mesh.bounds();
        if (b == null) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.peekScissorStack();
        int x0 = (int) Math.floor(b[0]);
        int y0 = (int) Math.floor(b[1]);
        ScreenRectangle area = new ScreenRectangle(x0, y0, (int) Math.ceil(b[2]) - x0 + 1, (int) Math.ceil(b[3]) - y0 + 1).transformMaxBounds(pose);
        ScreenRectangle bounds = scissor != null ? scissor.intersection(area) : area;
        graphics.submitGuiElementRenderState(new WheelMeshRenderState(pose, mesh.positions(), mesh.colors(), scissor, bounds));
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (int v = 0; v < colors.length; v++) {
            consumer.addVertexWith2DPose(pose, positions[v * 2], positions[v * 2 + 1]).setColor(colors[v]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}

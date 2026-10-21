package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * Draws the placements of the current dimension: ghost meshes within the block budget, spent most recently
 * selected first, and box outlines coloured by state. A FOLLOWING placement only shows while the remote is held.
 */
final class GhostRenderer {

    static final int FOLLOWING_COLOR = 0xFFFFD040;
    static final int ANCHORED_COLOR = 0xFF40E060;
    static final int UNAVAILABLE_COLOR = 0xFFFF4040;
    static final int OTHER_COLOR = 0x80FFFFFF;

    private final PlacementSession session;
    private final GhostMeshes meshes;
    private final BooleanSupplier remoteHeld;
    private int lastMeshes;
    private int lastOutlinesOnly;

    GhostRenderer(PlacementSession session, GhostMeshes meshes, BooleanSupplier remoteHeld) {
        this.session = session;
        this.meshes = meshes;
        this.remoteHeld = remoteHeld;
    }

    void render(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        String dimension = level.dimension().identifier().toString();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        Optional<UUID> selected = session.placements().selected().map(Placement::id);
        boolean holding = remoteHeld.getAsBoolean();
        int budget = HeavymaticaConfig.ghostBlockBudget();
        float opacity = HeavymaticaConfig.ghostOpacity();
        float lineWidth = minecraft.gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
        int used = 0;
        int meshesDrawn = 0;
        int outlinesOnly = 0;
        for (Placement placement : session.placements().inDimension(dimension)) {
            if (placement.following() && !holding) {
                continue;
            }
            Bounds bounds = PlacementTransform.bounds(placement);
            SchematicModel model = session.model(placement);
            GhostMesh mesh = model == null ? null : meshes.mesh(placement, model);
            if (mesh != null && used + mesh.blocks() <= budget) {
                used += mesh.blocks();
                meshesDrawn++;
                mesh.draw(event.getModelViewMatrix(),
                        new Vector3f((float) (bounds.minX() - camera.x), (float) (bounds.minY() - camera.y), (float) (bounds.minZ() - camera.z)), opacity);
            } else {
                outlinesOnly++;
            }
            boolean isSelected = selected.isPresent() && selected.get().equals(placement.id());
            int color = session.unavailable(placement) ? UNAVAILABLE_COLOR
                    : !isSelected ? OTHER_COLOR
                    : placement.following() ? FOLLOWING_COLOR : ANCHORED_COLOR;
            ShapeRenderer.renderShape(pose, lines, Shapes.create(0, 0, 0, bounds.sizeX(), bounds.sizeY(), bounds.sizeZ()),
                    bounds.minX() - camera.x, bounds.minY() - camera.y, bounds.minZ() - camera.z, color, isSelected ? lineWidth * 2.0F : lineWidth);
        }
        buffers.endBatch(RenderTypes.lines());
        lastMeshes = meshesDrawn;
        lastOutlinesOnly = outlinesOnly;
    }

    /** Meshes drawn and placements shown as outlines only in the last frame, for development checks. */
    String lastFrame() {
        return lastMeshes + " meshes drawn, " + lastOutlinesOnly + " outlines only";
    }
}

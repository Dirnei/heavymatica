package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import io.github.dirnei.mc.heavymatica.shaders.ShaderPacks;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Draws the placements of the current dimension: ghost meshes within the block budget, spent most recently
 * selected first, and box outlines coloured by state. A FOLLOWING placement only shows while the remote is held.
 */
final class GhostRenderer {

    static final int FOLLOWING_COLOR = 0xFFFFD040;
    static final int ANCHORED_COLOR = 0xFF40E060;
    static final int LOCKED_COLOR = 0xFF40D0F0;
    static final int UNAVAILABLE_COLOR = 0xFFFF4040;
    static final int OTHER_COLOR = 0x80FFFFFF;

    private final PlacementSession session;
    private final GhostMeshes meshes;
    private final BooleanSupplier remoteHeld;
    private final InsideGhost inside = new InsideGhost();
    private int lastMeshes;
    private int lastOutlinesOnly;
    private String lastCells = "";
    private Set<UUID> drawnThroughCells = Set.of();
    private @Nullable InHandFilter inHand;

    GhostRenderer(PlacementSession session, GhostMeshes meshes, BooleanSupplier remoteHeld) {
        this.session = session;
        this.meshes = meshes;
        this.remoteHeld = remoteHeld;
    }

    /** Draws the placements after the translucent blocks, unless a shader pack is in use. */
    void renderInLevel(Matrix4fc modelView) {
        if (!ShaderPacks.inUse()) {
            render(modelView);
        }
    }

    /**
     * Draws the placements on top of a shader pack's final image, before the hand clears the depth buffer. The pack
     * replaces the shaders of everything drawn during the level, which hides the ghosts there.
     */
    void renderAfterLevel(Matrix4fc modelView) {
        if (!ShaderPacks.inUse()) {
            return;
        }
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(modelView);
        try {
            render(modelView);
        } finally {
            modelViewStack.popMatrix();
        }
    }

    private void render(Matrix4fc modelView) {
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
        float placeholderOpacity = HeavymaticaConfig.ghostShowUnknownBlocks() ? opacity : 0.0F;
        float lineWidth = minecraft.gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
        PoseStack pose = new PoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
        int used = 0;
        int meshesDrawn = 0;
        int outlinesOnly = 0;
        StringBuilder cellsDrawn = new StringBuilder();
        Set<UUID> throughCells = new HashSet<>();
        Set<UUID> insideDrawn = new HashSet<>();
        InHandFilter filter = InHandFilter.current();
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
                Vector3f offset = new Vector3f((float) (bounds.minX() - camera.x), (float) (bounds.minY() - camera.y), (float) (bounds.minZ() - camera.z));
                GhostCells cells = GhostMeshes.usesCells(placement) ? meshes.cells(placement, model) : null;
                WorldComparison comparison = cells == null ? null : session.comparison(placement);
                if (comparison != null) {
                    Layers.Range layers = Layers.visible(placement, PlacementSession.feetLayer(placement));
                    ToDoubleFunction<Block> alpha = placement.inHand() ? filter.alpha(comparison) : block -> opacity;
                    GpuBuffer mask = comparison.mask();
                    // The in-hand filter never matches a block the game does not know.
                    float placeholders = placement.inHand() ? 0.0F : placeholderOpacity;
                    // Boxes go first: the ghosts' depth pass would otherwise hide every box behind a ghost block, and the
                    // boxes should only hide behind real blocks.
                    comparison.boxes().update();
                    comparison.boxes().draw(modelView, offset, layers.from(), layers.to());
                    int full = cells.draw(modelView, offset, layers, block -> alpha.applyAsDouble(block) >= opacity ? opacity : 0.0,
                            placeholders, mask);
                    int dimmed = cells.draw(modelView, offset, layers, block -> {
                        double value = alpha.applyAsDouble(block);
                        return value < opacity ? value : 0.0;
                    }, 0.0F, mask);
                    ComparisonState state = comparison.state();
                    inside.draw(placement, model, bounds, camera, modelView, offset, true,
                            planned -> eyeShows(state, layers, bounds, camera),
                            (x, y, z, planned) -> layers.contains(y) && state.state(state.index(x, y, z)).showsGhost()
                                    ? (float) alpha.applyAsDouble(planned.getBlock()) : 0.0F);
                    insideDrawn.add(placement.id());
                    throughCells.add(placement.id());
                    cellsDrawn.append(" ").append(placement.name()).append(" layers ").append(layers.from()).append('-').append(layers.to())
                            .append(": ").append(full).append(" full, ").append(dimmed).append(" dimmed");
                } else {
                    mesh.draw(modelView, offset, opacity, placeholderOpacity);
                    inside.draw(placement, model, bounds, camera, modelView, offset, false, planned -> true, (x, y, z, planned) -> opacity);
                    insideDrawn.add(placement.id());
                }
            } else {
                outlinesOnly++;
            }
            boolean isSelected = selected.isPresent() && selected.get().equals(placement.id());
            int color = session.unavailable(placement) ? UNAVAILABLE_COLOR
                    : !isSelected ? OTHER_COLOR
                    : placement.following() ? FOLLOWING_COLOR
                    : placement.locked() ? LOCKED_COLOR : ANCHORED_COLOR;
            ShapeRenderer.renderShape(pose, lines, Shapes.create(0, 0, 0, bounds.sizeX(), bounds.sizeY(), bounds.sizeZ()),
                    bounds.minX() - camera.x, bounds.minY() - camera.y, bounds.minZ() - camera.z, color, isSelected ? lineWidth * 2.0F : lineWidth);
        }
        buffers.endBatch(RenderTypes.lines());
        inside.retain(insideDrawn);
        lastMeshes = meshesDrawn;
        lastOutlinesOnly = outlinesOnly;
        lastCells = cellsDrawn.toString();
        drawnThroughCells = throughCells;
        inHand = filter;
    }

    /** True when the cell the camera is in shows its ghost: in the visible layers and not built yet. */
    private static boolean eyeShows(ComparisonState state, Layers.Range layers, Bounds bounds, Vec3 camera) {
        int x = (int) Math.floor(camera.x - bounds.minX());
        int y = (int) Math.floor(camera.y - bounds.minY());
        int z = (int) Math.floor(camera.z - bounds.minZ());
        return layers.contains(y) && state.stateOrUnknown(x, y, z).showsGhost();
    }

    /** The placements whose ghosts the last frame drew cell by cell, with the comparison mask and the boxes. */
    Set<UUID> drawnThroughCells() {
        return drawnThroughCells;
    }

    /** The in-hand filter the last frame drew with, or the current one before the first frame. */
    InHandFilter inHand() {
        return inHand != null ? inHand : InHandFilter.current();
    }

    /** Meshes drawn and placements shown as outlines only in the last frame, for development checks. */
    String lastFrame() {
        return lastMeshes + " meshes drawn, " + lastOutlinesOnly + " outlines only" + (lastCells.isEmpty() ? "" : ", cells:" + lastCells);
    }
}

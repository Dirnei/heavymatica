package io.github.dirnei.mc.heavymatica.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

/**
 * Writes the models a display model submits straight into per-texture vertex lists, the way vanilla's model feature
 * renderers would draw them. Everything that is not textured geometry (text, items, shadows, block models, which the
 * block model renderer already covers) is left out.
 */
final class CapturingSubmits implements SubmitNodeCollector {

    private final SpecialGeometry.Parts parts = new SpecialGeometry.Parts();

    SpecialGeometry.Baked baked() {
        return parts.baked();
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
            int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        VertexConsumer buffer = buffer(renderType, sprite);
        if (buffer != null) {
            model.setupAnim(state);
            model.renderToBuffer(poseStack, buffer, lightCoords, overlayCoords, tintedColor);
        }
    }

    @Override
    public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
            @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil, int tintedColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {
        VertexConsumer buffer = buffer(renderType, sprite);
        if (buffer != null) {
            modelPart.render(poseStack, buffer, lightCoords, overlayCoords, tintedColor);
        }
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
        VertexConsumer buffer = buffer(renderType, null);
        if (buffer != null) {
            customGeometryRenderer.render(poseStack.last(), buffer);
        }
    }

    /** The buffer for a submit: the sprite's atlas with its UVs mapped, else the render type's texture; null without one. */
    private @Nullable VertexConsumer buffer(RenderType renderType, @Nullable TextureAtlasSprite sprite) {
        ChunkSectionLayer layer = renderType.hasBlending() ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.CUTOUT;
        if (sprite != null) {
            return sprite.wrap(parts.of(sprite.atlasLocation(), layer));
        }
        Identifier texture = texture(renderType);
        return texture == null ? null : parts.of(texture, layer);
    }

    private static @Nullable Identifier texture(RenderType renderType) {
        RenderSetup.TextureBinding binding = renderType.state.textures.get("Sampler0");
        return binding == null ? null : binding.location();
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
            double distanceToCameraSq, CameraRenderState camera) {
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
            int lightCoords, int color, int backgroundColor, int outlineColor) {
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
            int overlayCoords, int outlineColor) {
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
            List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
    }

    @Override
    public void submitParticleGroup(ParticleGroupRenderer particleGroupRenderer) {
    }
}

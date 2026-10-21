package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import io.github.dirnei.mc.heavymatica.thumbnail.SchematicView;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * A schematic as placed: positions are in the rotated box (0-based) and block states are mirrored, then rotated, like
 * vanilla structure templates. Fully lit and block-only, for ghost meshes. Unknown blocks read as air.
 */
final class GhostView implements BlockAndTintGetter {

    /** Plains grass and foliage, since a schematic has no biome. */
    private static final int DEFAULT_TINT = 0xFF91BD59;

    private final SchematicModel model;
    private final Turn rotation;
    private final Flip mirror;
    private final BlockState[] palette;
    private final int sizeX;
    private final int sizeZ;

    GhostView(SchematicModel model, Turn rotation, Flip mirror) {
        this.model = model;
        this.rotation = rotation;
        this.mirror = mirror;
        Rotation vanillaRotation = Rotation.values()[rotation.ordinal()];
        Mirror vanillaMirror = Mirror.values()[mirror.ordinal()];
        Map<String, BlockState> resolved = new HashMap<>();
        this.palette = model.palette().stream()
                .map(state -> resolved.computeIfAbsent(state, text -> SchematicView.resolve(text).mirror(vanillaMirror).rotate(vanillaRotation)))
                .toArray(BlockState[]::new);
        this.sizeX = rotation.swapsAxes() ? model.length() : model.width();
        this.sizeZ = rotation.swapsAxes() ? model.width() : model.length();
    }

    int sizeX() {
        return sizeX;
    }

    int sizeY() {
        return model.height();
    }

    int sizeZ() {
        return sizeZ;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0 || pos.getX() >= sizeX || pos.getY() >= model.height() || pos.getZ() >= sizeZ) {
            return Blocks.AIR.defaultBlockState();
        }
        int[] schematic = new int[2];
        PlacementTransform.toSchematic(rotation, mirror, model.width(), model.length(), pos.getX(), pos.getZ(), schematic);
        return palette[model.blocks()[SchematicModel.index(schematic[0], pos.getY(), schematic[1], model.width(), model.length())]];
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return model.height();
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        return DEFAULT_TINT;
    }
}

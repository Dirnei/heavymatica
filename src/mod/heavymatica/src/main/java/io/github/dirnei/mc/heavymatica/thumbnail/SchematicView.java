package io.github.dirnei.mc.heavymatica.thumbnail;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * A schematic as a fully lit, block-only world for block model rendering. Blocks the game does not know (other
 * mods, removed blocks) read as air, so a thumbnail still renders everything else.
 */
public final class SchematicView implements BlockAndTintGetter {

    /** Plains grass and foliage, since a schematic has no biome. */
    private static final int DEFAULT_TINT = 0xFF91BD59;

    private final SchematicModel model;
    private final BlockState[] palette;

    SchematicView(SchematicModel model) {
        this.model = model;
        Map<String, BlockState> resolved = new HashMap<>();
        this.palette = model.palette().stream().map(state -> resolved.computeIfAbsent(state, SchematicView::resolve)).toArray(BlockState[]::new);
    }

    public static BlockState resolve(String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, state, false).blockState();
        } catch (CommandSyntaxException e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    SchematicModel model() {
        return model;
    }

    boolean contains(BlockPos pos) {
        return pos.getX() >= 0 && pos.getY() >= 0 && pos.getZ() >= 0 && pos.getX() < model.width() && pos.getY() < model.height() && pos.getZ() < model.length();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (!contains(pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return palette[model.blocks()[SchematicModel.index(pos.getX(), pos.getY(), pos.getZ(), model.width(), model.length())]];
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

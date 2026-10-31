package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.blocks.SchematicTints;
import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import io.github.dirnei.mc.heavymatica.thumbnail.SchematicView;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * A schematic as placed: positions are in the rotated box (0-based) and block states are swapped, mirrored, then
 * rotated, like vanilla structure templates. Fully lit and block-only, for ghost meshes. Unknown blocks read as air.
 */
final class GhostView implements BlockAndTintGetter {

    private final SchematicModel model;
    private final Turn rotation;
    private final Flip mirror;
    private final String[] entries;
    private final BlockState[] palette;
    private final int sizeX;
    private final int sizeZ;

    /** @param swaps original block ID to target block ID; a target the game does not know leaves the original */
    GhostView(SchematicModel model, Turn rotation, Flip mirror, Map<String, String> swaps) {
        this.model = model;
        this.rotation = rotation;
        this.mirror = mirror;
        Rotation vanillaRotation = Rotation.values()[rotation.ordinal()];
        Mirror vanillaMirror = Mirror.values()[mirror.ordinal()];
        this.entries = model.palette().stream().map(state -> {
            String swapped = BlockStates.swapped(state, swaps);
            return swapped.equals(state) || target(swapped) == null ? state : swapped;
        }).toArray(String[]::new);
        Map<String, BlockState> resolved = new HashMap<>();
        this.palette = new BlockState[entries.length];
        for (int i = 0; i < entries.length; i++) {
            String original = model.palette().get(i);
            palette[i] = resolved.computeIfAbsent(entries[i], text -> {
                BlockState state = text.equals(original) ? SchematicView.resolve(text) : lenient(target(text), text);
                return state.mirror(vanillaMirror).rotate(vanillaRotation);
            });
        }
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
        return palette[paletteIndex(pos.getX(), pos.getY(), pos.getZ(), new int[2])];
    }

    /** The schematic palette index at a position inside the view; {@code scratch} holds two ints. */
    int paletteIndex(int x, int y, int z, int[] scratch) {
        PlacementTransform.toSchematic(rotation, mirror, model.width(), model.length(), x, z, scratch);
        return model.blocks()[SchematicModel.index(scratch[0], y, scratch[1], model.width(), model.length())];
    }

    /** A palette entry's state string after its swap, or as stored when it has none or its target is unknown. */
    String entry(int paletteIndex) {
        return entries[paletteIndex];
    }

    /** A palette entry as placed: swapped, mirrored and rotated, air when the game does not know it. */
    BlockState placed(int paletteIndex) {
        return palette[paletteIndex];
    }

    /** Every state the view can show. */
    List<BlockState> placedStates() {
        return Arrays.stream(palette).distinct().toList();
    }

    SchematicModel model() {
        return model;
    }

    private static @Nullable Block target(String state) {
        Identifier id = Identifier.tryParse(BlockStates.id(state));
        return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    /** The block's default state with every property of {@code state} that the block has and whose value it accepts. */
    private static BlockState lenient(Block block, String state) {
        BlockState result = block.defaultBlockState();
        for (Map.Entry<String, String> property : BlockStates.properties(state).entrySet()) {
            Property<?> known = block.getStateDefinition().getProperty(property.getKey());
            if (known != null) {
                result = with(result, known, property.getValue());
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
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
        return SchematicTints.tint(pos, color);
    }
}

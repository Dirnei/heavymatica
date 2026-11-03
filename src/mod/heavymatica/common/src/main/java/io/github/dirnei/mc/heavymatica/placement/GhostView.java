package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.blocks.BlockResolution.Resolved;
import io.github.dirnei.mc.heavymatica.blocks.BlockResolver;
import io.github.dirnei.mc.heavymatica.blocks.SchematicTints;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * A schematic as placed: positions are in the rotated box (0-based) and block states are swapped, mirrored, then
 * rotated, like vanilla structure templates. Fully lit and block-only, for ghost meshes. Blocks the game does not
 * know read as air and are flagged.
 */
public final class GhostView implements BlockAndTintGetter {

    private final SchematicModel model;
    private final Turn rotation;
    private final Flip mirror;
    private final String[] entries;
    private final BlockState[] palette;
    private final boolean[] unknown;
    private final int sizeX;
    private final int sizeZ;

    /** @param swaps block ID as written in the file to target block ID; a target the game does not know leaves the original */
    GhostView(SchematicModel model, Turn rotation, Flip mirror, Map<String, String> swaps) {
        this.model = model;
        this.rotation = rotation;
        this.mirror = mirror;
        Rotation vanillaRotation = Rotation.values()[rotation.ordinal()];
        Mirror vanillaMirror = Mirror.values()[mirror.ordinal()];
        int size = model.palette().size();
        this.entries = new String[size];
        this.palette = new BlockState[size];
        this.unknown = new boolean[size];
        Map<String, BlockState> placed = new HashMap<>();
        for (int i = 0; i < size; i++) {
            Resolved resolved = BlockResolver.resolve(model.palette().get(i), model.dataVersion(), swaps);
            entries[i] = resolved.shown();
            BlockState state = BlockResolver.state(resolved);
            unknown[i] = state == null;
            palette[i] = state == null ? Blocks.AIR.defaultBlockState()
                    : placed.computeIfAbsent(resolved.shown(), ignored -> state.mirror(vanillaMirror).rotate(vanillaRotation));
        }
        this.sizeX = rotation.swapsAxes() ? model.length() : model.width();
        this.sizeZ = rotation.swapsAxes() ? model.width() : model.length();
    }

    /** The placement's schematic as the placement shows it. */
    public static GhostView of(SchematicModel model, Placement placement) {
        return new GhostView(model, placement.rotation(), placement.mirror(), placement.swaps());
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return model.height();
    }

    public int sizeZ() {
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
    public int paletteIndex(int x, int y, int z, int[] scratch) {
        PlacementTransform.toSchematic(rotation, mirror, model.width(), model.length(), x, z, scratch);
        return model.blocks()[SchematicModel.index(scratch[0], y, scratch[1], model.width(), model.length())];
    }

    /** A palette entry's state string as shown: upgraded, swapped and reduced to what the block accepts. */
    public String entry(int paletteIndex) {
        return entries[paletteIndex];
    }

    /** A palette entry as placed: swapped, mirrored and rotated, air when the game does not know it. */
    public BlockState placed(int paletteIndex) {
        return palette[paletteIndex];
    }

    /** True when the game does not know the block a palette entry shows. */
    boolean unknown(int paletteIndex) {
        return unknown[paletteIndex];
    }

    /** True when the position lies inside the view and the game does not know its block. */
    boolean unknownAt(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= model.height() || z >= sizeZ) {
            return false;
        }
        return unknown[paletteIndex(x, y, z, new int[2])];
    }

    /** The faces of an unknown position's placeholder that a neighbouring placeholder or solid block covers. */
    boolean placeholderFaceCovered(int x, int y, int z, Direction face) {
        int nx = x + face.getStepX();
        int ny = y + face.getStepY();
        int nz = z + face.getStepZ();
        return unknownAt(nx, ny, nz) || getBlockState(new BlockPos(nx, ny, nz)).isSolidRender();
    }

    /** Every state the view can show. */
    List<BlockState> placedStates() {
        return Arrays.stream(palette).distinct().toList();
    }

    /** The positions of a schematic whose block the game does not know after its swaps, without building a view. */
    static UnknownBlocks unknownBlocks(SchematicModel model, Map<String, String> swaps) {
        List<String> palette = model.palette();
        return UnknownBlocks.count(model.blocks(), palette.size(), index -> {
            Resolved resolved = BlockResolver.resolve(palette.get(index), model.dataVersion(), swaps);
            return resolved.known() ? null : resolved.blockId();
        });
    }

    SchematicModel model() {
        return model;
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

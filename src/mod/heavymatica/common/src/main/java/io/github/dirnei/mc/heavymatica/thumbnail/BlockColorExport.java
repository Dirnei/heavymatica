package io.github.dirnei.mc.heavymatica.thumbnail;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import io.github.dirnei.mc.heavymatica.blocks.SchematicTints;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Writes the cloud's block colour table: per block, the average colour of its model's upward and side faces with
 * their default tint, its alpha, and a rough shape, for the cloud's thumbnails of schematics uploaded without one.
 * Reads baked models and sprite images, so it runs on the render thread.
 */
public final class BlockColorExport {

    private static final float TRANSLUCENT_ALPHA = 0.6F;
    private static final double FULL_FOOTPRINT = 0.8;

    private record Entry(int top, int side, float alpha, String shape, float height) {
    }

    /** Running sums of alpha-weighted colour. */
    private static final class Average {
        double red;
        double green;
        double blue;
        double alpha;
        long pixels;

        void add(TextureAtlasSprite sprite, int tint) {
            NativeImage image = sprite.contents().getOriginalImage();
            int width = Math.min(sprite.contents().width(), image.getWidth());
            int height = Math.min(sprite.contents().height(), image.getHeight());
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = image.getPixel(x, y);
                    double weight = ARGB.alpha(pixel) / 255.0;
                    red += weight * ARGB.red(pixel) * ARGB.red(tint) / 255.0;
                    green += weight * ARGB.green(pixel) * ARGB.green(tint) / 255.0;
                    blue += weight * ARGB.blue(pixel) * ARGB.blue(tint) / 255.0;
                    alpha += weight;
                    pixels++;
                }
            }
        }

        boolean isEmpty() {
            return alpha <= 0;
        }

        int rgb() {
            return ARGB.color(0, (int) Math.round(red / alpha), (int) Math.round(green / alpha), (int) Math.round(blue / alpha)) & 0xFFFFFF;
        }

        float meanAlpha() {
            return pixels == 0 ? 0 : (float) (alpha / pixels);
        }
    }

    private BlockColorExport() {
    }

    /** Writes the table for every registered block to {@code target}; returns how many blocks it holds. */
    public static int export(Path target) throws IOException {
        Minecraft minecraft = Minecraft.getInstance();
        BlockColors tints = minecraft.getBlockColors();
        Map<String, Entry> table = new TreeMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (state.isAir()) {
                continue;
            }
            table.put(BuiltInRegistries.BLOCK.getKey(block).toString(),
                    entry(state, minecraft.getModelManager().getBlockStateModelSet().get(state), tints, minecraft.getModelManager().getFluidStateModelSet()));
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, json(table), StandardCharsets.UTF_8);
        return table.size();
    }

    private static Entry entry(BlockState state, BlockStateModel model, BlockColors tints, FluidStateModelSet fluids) {
        String shape = shape(state);
        float height = shape.equals("slab") ? height(state) : 1F;
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        Average top = new Average();
        Average side = new Average();
        Average any = new Average();
        boolean solid = false;
        boolean translucent = false;
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> quads = new ArrayList<>(part.getQuads(null));
            for (Direction direction : Direction.values()) {
                quads.addAll(part.getQuads(direction));
            }
            for (BakedQuad quad : quads) {
                BakedQuad.MaterialInfo material = quad.materialInfo();
                int tint = material.isTinted() ? tint(tints.getTintSource(state, material.tintIndex()), state) : -1;
                solid |= material.layer() == ChunkSectionLayer.SOLID;
                translucent |= material.layer() == ChunkSectionLayer.TRANSLUCENT;
                if (quad.direction() == Direction.UP) {
                    top.add(material.sprite(), tint);
                } else if (quad.direction() == Direction.NORTH) {
                    side.add(material.sprite(), tint);
                }
                any.add(material.sprite(), tint);
            }
        }

        if (any.isEmpty() || state.getBlock() instanceof LiquidBlock) {
            return withoutModel(state, shape, height, fluids);
        }
        float alpha;
        if (solid) {
            alpha = 1F;
        } else if (translucent) {
            alpha = Math.min(any.meanAlpha(), TRANSLUCENT_ALPHA);
        } else {
            // Cutout textures: thin models are drawn as a narrow column already, so only full blocks keep their gaps.
            alpha = shape.equals("cube") ? Math.min(1F, any.meanAlpha() + 0.05F) : 1F;
        }
        return new Entry((top.isEmpty() ? any : top).rgb(), (side.isEmpty() ? any : side).rgb(), alpha > 0.95F ? 1F : alpha, shape, height);
    }

    /** Liquids from their fluid's still texture; blocks a block-entity renderer draws, such as chests and beds, from their map colour. */
    private static Entry withoutModel(BlockState state, String shape, float height, FluidStateModelSet fluids) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            FluidModel model = fluids.get(fluid);
            Average still = new Average();
            int tint = tint(model.tintSource(), state);
            if (tint == -1 && fluid.getType().isSame(Fluids.WATER)) {
                // Water's tint comes from the biome; schematics are drawn in the plains.
                tint = SchematicTints.tint(BlockPos.ZERO, BiomeColors.WATER_COLOR_RESOLVER);
            }
            still.add(model.stillMaterial().sprite(), tint);
            return new Entry(still.rgb(), still.rgb(), model.layer() == ChunkSectionLayer.TRANSLUCENT ? TRANSLUCENT_ALPHA : 1F, "cube", 1F);
        }
        MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (state.getRenderShape() == RenderShape.INVISIBLE || shape.equals("none") || mapColor == MapColor.NONE) {
            return new Entry(0, 0, 0F, "none", 1F);
        }
        return new Entry(mapColor.col, mapColor.col, 1F, shape, height);
    }

    private static int tint(@Nullable BlockTintSource source, BlockState state) {
        return source == null ? -1 : ARGB.opaque(source.color(state));
    }

    private static String shape(BlockState state) {
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) {
            return state.getFluidState().isEmpty() ? "none" : "cube";
        }
        AABB bounds = shape.bounds();
        if (bounds.getXsize() >= FULL_FOOTPRINT && bounds.getZsize() >= FULL_FOOTPRINT) {
            return bounds.getYsize() >= 1 - 1E-6 ? "cube" : "slab";
        }
        return "cross";
    }

    private static float height(BlockState state) {
        return (float) state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds().getYsize();
    }

    private static String json(Map<String, Entry> table) {
        StringBuilder out = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Entry> row : table.entrySet()) {
            Entry entry = row.getValue();
            out.append(String.format(Locale.ROOT, "  \"%s\": { \"top\": \"#%06x\", \"side\": \"#%06x\", \"alpha\": %.2f, \"shape\": \"%s\", \"height\": %.3f }",
                    row.getKey(), entry.top(), entry.side(), entry.alpha(), entry.shape(), entry.height()));
            out.append(++i < table.size() ? ",\n" : "\n");
        }
        return out.append("}\n").toString();
    }
}

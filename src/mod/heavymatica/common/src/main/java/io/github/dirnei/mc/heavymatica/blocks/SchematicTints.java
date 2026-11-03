package io.github.dirnei.mc.heavymatica.blocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.Nullable;

/** Tints for blocks of a schematic, which has no biome: grass, foliage and water of the plains. */
public final class SchematicTints {

    /** The plains colours, for when no world is loaded to look the biome up in. */
    private static final int PLAINS_GRASS = 0xFF91BD59;
    private static final int PLAINS_FOLIAGE = 0xFF77AB2F;
    private static final int PLAINS_DRY_FOLIAGE = 0xFFA0A69C;
    private static final int PLAINS_WATER = 0xFF3F76E4;

    private SchematicTints() {
    }

    public static int tint(BlockPos pos, ColorResolver color) {
        Biome plains = plains();
        if (plains != null) {
            return color.getColor(plains, pos.getX(), pos.getZ());
        }
        if (color == BiomeColors.WATER_COLOR_RESOLVER) {
            return PLAINS_WATER;
        }
        if (color == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
            return PLAINS_FOLIAGE;
        }
        return color == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER ? PLAINS_DRY_FOLIAGE : PLAINS_GRASS;
    }

    private static @Nullable Biome plains() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess().lookupOrThrow(Registries.BIOME).getValue(Biomes.PLAINS);
    }
}

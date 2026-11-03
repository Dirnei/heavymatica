package io.github.dirnei.mc.heavymatica.blocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;

/**
 * Vanilla's directional face shading. Shader mods may make {@link CardinalLighting#byFace} return the top face's
 * shade for every face while a shader pack is in use, which would leave schematic geometry flat; the record's own
 * values are read instead.
 */
public final class FaceShade {

    private FaceShade() {
    }

    /** The shade of a face as vanilla lights it without shaders. */
    public static float of(CardinalLighting lighting, Direction face) {
        return switch (face) {
            case DOWN -> lighting.down();
            case UP -> lighting.up();
            case NORTH -> lighting.north();
            case SOUTH -> lighting.south();
            case WEST -> lighting.west();
            case EAST -> lighting.east();
        };
    }

    /** What a colour shaded with {@code byFace} must be multiplied with to get vanilla's shade; 1 when they agree. */
    public static float correction(CardinalLighting lighting, Direction face) {
        float applied = lighting.byFace(face);
        return applied == 0.0F ? 1.0F : of(lighting, face) / applied;
    }
}

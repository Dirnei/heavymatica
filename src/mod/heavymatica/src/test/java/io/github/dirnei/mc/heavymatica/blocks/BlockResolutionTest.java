package io.github.dirnei.mc.heavymatica.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.blocks.BlockResolution.Property;
import io.github.dirnei.mc.heavymatica.blocks.BlockResolution.Resolved;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockResolutionTest {

    private static final int GAME = 4000;

    private static Property values(String... accepted) {
        Set<String> set = Set.of(accepted);
        return set::contains;
    }

    private static final Map<String, Map<String, Property>> BLOCKS = Map.of(
            "minecraft:stone", Map.of(),
            "minecraft:short_grass", Map.of(),
            "minecraft:fern", Map.of(),
            "minecraft:iron_chain", Map.of("axis", values("x", "y", "z")),
            "minecraft:oak_stairs", Map.of("facing", values("north", "east", "south", "west"), "half", values("top", "bottom")),
            "minecraft:spruce_stairs", Map.of("facing", values("north", "east", "south", "west"), "half", values("top", "bottom")),
            "minecraft:cobblestone_wall", Map.of("north", values("none", "low", "tall"), "up", values("true", "false")));

    /** Renames grass from version 3578 on, and turns old wall sides into low ones before 2600. */
    private static final BlockResolution.Upgrader UPGRADER = (state, from) -> {
        if (state.equals("minecraft:grass") && from <= 3578) {
            return "minecraft:short_grass";
        }
        if (state.startsWith("minecraft:cobblestone_wall") && from < 2600) {
            return state.replace("north=true", "north=low");
        }
        return state;
    };

    private final BlockResolution resolution = new BlockResolution(UPGRADER, BLOCKS::get, GAME);

    @Test
    void unknownPropertyValueKeepsTheRest() {
        Resolved resolved = resolution.resolve("minecraft:oak_stairs[facing=north,half=middle]", GAME, Map.of());

        assertTrue(resolved.known());
        assertEquals("minecraft:oak_stairs[facing=north]", resolved.shown());
        assertEquals(Map.of("facing", "north"), resolved.properties());
    }

    @Test
    void extraPropertyIsDropped() {
        Resolved resolved = resolution.resolve("minecraft:stone[glowing=true]", GAME, Map.of());

        assertTrue(resolved.known());
        assertEquals("minecraft:stone", resolved.shown());
    }

    @Test
    void unknownBlock() {
        Resolved resolved = resolution.resolve("create:shaft[axis=y]", GAME, Map.of());

        assertFalse(resolved.known());
        assertEquals("create:shaft[axis=y]", resolved.shown());
        assertEquals("create:shaft", resolved.blockId());
    }

    @Test
    void oldEntryIsUpgraded() {
        assertEquals("minecraft:short_grass", resolution.resolve("minecraft:grass", 3578, Map.of()).shown());
        assertEquals("minecraft:cobblestone_wall[north=low,up=true]", resolution.resolve("minecraft:cobblestone_wall[north=true,up=true]", 2230, Map.of()).shown());
    }

    @Test
    void noUpgradeWithoutDataVersionOrFromNewerOne() {
        assertFalse(resolution.resolve("minecraft:grass", null, Map.of()).known());
        assertFalse(resolution.resolve("minecraft:grass", GAME, Map.of()).known());
        assertFalse(resolution.resolve("minecraft:grass", GAME + 1, Map.of()).known());
    }

    @Test
    void swapIsKeyedByTheIdAsWrittenAndAppliedAfterTheUpgrade() {
        Resolved resolved = resolution.resolve("minecraft:grass", 3578, Map.of("minecraft:grass", "minecraft:fern"));

        assertTrue(resolved.known());
        assertEquals("minecraft:fern", resolved.shown());
        assertEquals("minecraft:short_grass", resolution.resolve("minecraft:grass", 3578, Map.of("minecraft:short_grass", "minecraft:fern")).shown());
    }

    @Test
    void swapTakesTheUpgradedProperties() {
        Resolved resolved = resolution.resolve("minecraft:cobblestone_wall[north=true,up=true]", 2230,
                Map.of("minecraft:cobblestone_wall", "minecraft:oak_stairs"));

        assertEquals("minecraft:oak_stairs", resolved.shown());
        assertEquals("minecraft:spruce_stairs[facing=east,half=top]",
                resolution.resolve("minecraft:oak_stairs[facing=east,half=top]", GAME, Map.of("minecraft:oak_stairs", "minecraft:spruce_stairs")).shown());
    }

    @Test
    void swapMakesAnUnknownBlockKnown() {
        Resolved resolved = resolution.resolve("create:shaft[axis=x]", GAME, Map.of("create:shaft", "minecraft:iron_chain"));

        assertTrue(resolved.known());
        assertEquals("minecraft:iron_chain[axis=x]", resolved.shown());
    }

    @Test
    void unknownSwapTargetLeavesTheOriginal() {
        Resolved resolved = resolution.resolve("minecraft:oak_stairs[facing=east,half=top]", GAME, Map.of("minecraft:oak_stairs", "othermod:stairs"));

        assertTrue(resolved.known());
        assertEquals("minecraft:oak_stairs[facing=east,half=top]", resolved.shown());
    }
}

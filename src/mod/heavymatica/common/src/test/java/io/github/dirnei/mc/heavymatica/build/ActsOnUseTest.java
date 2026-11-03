package io.github.dirnei.mc.heavymatica.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The game's block classes are not on the unit-test classpath, so these stand-ins mirror their shape: the base declares
 * both use methods, chests, doors, levers and buttons override one, stone and planks are the plain block class.
 */
class ActsOnUseTest {

    static class Behaviour {
        protected Object useWithoutItem(Object state) {
            return null;
        }

        protected Object useItemOn(Object stack, Object state) {
            return null;
        }
    }

    static class Block extends Behaviour {
    }

    static class Chest extends Block {
        @Override
        protected Object useWithoutItem(Object state) {
            return "open";
        }
    }

    static class Door extends Block {
        @Override
        protected Object useWithoutItem(Object state) {
            return "toggle";
        }
    }

    static class FaceAttached extends Block {
    }

    static class Lever extends FaceAttached {
        @Override
        protected Object useWithoutItem(Object state) {
            return "pull";
        }
    }

    static class Button extends FaceAttached {
        @Override
        protected Object useWithoutItem(Object state) {
            return "press";
        }
    }

    static class Cauldron extends Block {
        @Override
        protected Object useItemOn(Object stack, Object state) {
            return "fill";
        }
    }

    static class WaxedChest extends Chest {
    }

    private final ActsOnUse acts = new ActsOnUse(Behaviour.class, Set.of("useWithoutItem", "useItemOn"));

    @Test
    void usableBlocksAct() {
        assertTrue(acts.acts(Chest.class));
        assertTrue(acts.acts(Door.class));
        assertTrue(acts.acts(Lever.class));
        assertTrue(acts.acts(Button.class));
        assertTrue(acts.acts(Cauldron.class));
    }

    @Test
    void anInheritedOverrideActs() {
        assertTrue(acts.acts(WaxedChest.class));
    }

    @Test
    void plainBlocksDoNotAct() {
        assertFalse(acts.acts(Block.class));
        assertFalse(acts.acts(FaceAttached.class));
        assertFalse(acts.acts(Behaviour.class));
    }
}

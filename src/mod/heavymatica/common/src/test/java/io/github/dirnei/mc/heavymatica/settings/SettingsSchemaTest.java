package io.github.dirnei.mc.heavymatica.settings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsSchemaTest {

    enum Side {
        LEFT,
        RIGHT,
    }

    /** Keeps values in a map, like a loaded file. */
    static final class MapBackend implements SettingsBackend {

        final Map<Setting<?>, Object> values = new HashMap<>();
        int saves;

        @SuppressWarnings("unchecked")
        @Override
        public <T> T get(Setting<T> setting) {
            return (T) values.getOrDefault(setting, setting.defaultValue());
        }

        @Override
        public <T> void set(Setting<T> setting, T value) {
            values.put(setting, value);
        }

        @Override
        public void save(Setting<?> setting) {
            saves++;
        }
    }

    @Test
    void commentsGoToTheNextPushOrDefine() {
        SettingsSchema schema = new SettingsSchema();
        Setting<String> url = schema.comment("Section comment.").push("cloud").define("url", "https://example.com", value -> true);
        Setting<Integer> size = schema.pop().push("cache").comment("Size line one.", "Size line two.").defineInRange("size", 10, 1, 20);
        schema.pop();

        SettingsSchema.Push cloud = (SettingsSchema.Push) schema.operations().getFirst();
        assertEquals("cloud", cloud.name());
        assertArrayEquals(new String[] {"Section comment."}, cloud.comment());
        assertNull(url.comment());
        assertArrayEquals(new String[] {"Size line one.", "Size line two."}, size.comment());
        assertNull(((SettingsSchema.Push) schema.operations().get(3)).comment());
    }

    @Test
    void pathsAndLookup() {
        SettingsSchema schema = new SettingsSchema();
        Setting<Double> opacity = schema.push("ghost").defineInRange("opacity", 0.4, 0.05, 1.0);
        schema.pop();

        assertEquals(List.of("ghost", "opacity"), opacity.path());
        assertEquals("ghost.opacity", opacity.dottedPath());
        assertSame(opacity, schema.setting("ghost.opacity"));
        assertNull(schema.setting("ghost.missing"));
        assertEquals(List.of(opacity), schema.settings());
    }

    @Test
    void valuesComeFromTheBackend() {
        SettingsSchema schema = new SettingsSchema();
        Setting<Boolean> pinned = schema.push("overlay").define("pinned", false);
        schema.pop();
        assertThrows(IllegalStateException.class, pinned::get);

        MapBackend backend = new MapBackend();
        schema.bind(backend);
        assertEquals(false, pinned.get());
        pinned.set(true);
        pinned.save();
        assertEquals(true, pinned.get());
        assertEquals(1, backend.saves);
    }

    @Test
    void acceptsOnlyValidValues() {
        SettingsSchema schema = new SettingsSchema();
        schema.push("all");
        Setting<Integer> size = schema.defineInRange("size", 1024, 64, 65_536);
        Setting<Double> opacity = schema.defineInRange("opacity", 0.4, 0.05, 1.0);
        Setting<Side> side = schema.defineEnum("side", Side.LEFT);
        Setting<String> item = schema.define("item", "minecraft:stick", value -> value instanceof String text && text.contains(":"));
        Setting<Boolean> flag = schema.define("flag", true);
        Setting<List<? extends Number>> steps = schema.defineList("steps", List.of(0.5), () -> 0.5, value -> value instanceof Number);
        schema.pop();

        assertEquals(1024, size.accept(1024));
        assertEquals(100, size.accept(100L));
        assertNull(size.accept(10));
        assertNull(size.accept(100.5));
        assertNull(size.accept("100"));
        assertEquals(1.0, opacity.accept(1));
        assertEquals(0.6, opacity.accept(0.6));
        assertNull(opacity.accept(0.01));
        assertEquals(Side.RIGHT, side.accept("right"));
        assertEquals(Side.LEFT, side.accept(Side.LEFT));
        assertNull(side.accept("TOP"));
        assertEquals("minecraft:blaze_rod", item.accept("minecraft:blaze_rod"));
        assertNull(item.accept("blaze_rod"));
        assertNull(flag.accept("true"));
        assertEquals(List.of(0.2, 1), steps.accept(List.of(0.2, "x", 1)));
        assertNull(steps.accept(0.2));
        assertInstanceOf(Integer.class, size.defaultValue());
    }

    @Test
    void unbalancedSectionsAreRejected() {
        SettingsSchema schema = new SettingsSchema();
        schema.push("open").define("flag", true);
        assertThrows(IllegalStateException.class, schema::operations);
        assertThrows(IllegalStateException.class, () -> new SettingsSchema().pop());
    }
}

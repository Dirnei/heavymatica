package io.github.dirnei.mc.heavymatica.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dirnei.mc.heavymatica.formats.Nbt;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CaptureSnapshotTest {

    /** Stone below y 68, air above, a chest at 3, 68, 3, and a configurable set of unloaded chunks. */
    private static class FakeWorld implements CaptureSnapshot.World {

        final Set<Long> unloaded = new HashSet<>();
        final List<int[]> requests = new ArrayList<>();
        final List<CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>>> pending = new ArrayList<>();
        boolean answerLater;

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return !unloaded.contains(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
        }

        void unload(int chunkX, int chunkZ) {
            unloaded.add(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
        }

        @Override
        public Object state(int x, int y, int z) {
            if (x == 3 && y == 68 && z == 3) {
                return "minecraft:chest[facing=north,type=single,waterlogged=false]";
            }
            return y < 68 ? "minecraft:stone" : "minecraft:air";
        }

        @Override
        public String name(Object state) {
            return (String) state;
        }

        @Override
        public CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>> blockEntities(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            requests.add(new int[] {minX, minY, minZ, maxX, maxY, maxZ});
            Map<SchematicModel.Pos, Nbt.Compound> found = new HashMap<>();
            if (minX <= 3 && 3 <= maxX && minY <= 68 && 68 <= maxY && minZ <= 3 && 3 <= maxZ) {
                LinkedHashMap<String, Nbt.Tag> chest = new LinkedHashMap<>();
                chest.put("id", new Nbt.Text("minecraft:chest"));
                found.put(new SchematicModel.Pos(3, 68, 3), new Nbt.Compound(chest));
            }
            if (answerLater) {
                CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>> later = new CompletableFuture<>();
                pending.add(later);
                return later;
            }
            return CompletableFuture.completedFuture(found);
        }
    }

    private static final CaptureBox.Bounds BOX = new CaptureBox.Bounds(-4, 64, 0, 24, 8, 5);

    @Test
    void reads_the_box_in_canonical_order_with_positions_relative_to_the_box() {
        FakeWorld world = new FakeWorld();
        CaptureSnapshot snapshot = new CaptureSnapshot(BOX, world, 4671);

        assertEquals(CaptureSnapshot.Outcome.DONE, snapshot.step(Long.MAX_VALUE));
        SchematicModel model = snapshot.model();

        assertEquals(List.of(24, 8, 5), List.of(model.width(), model.height(), model.length()));
        assertEquals("minecraft:stone", model.stateAt(0, 3, 0));
        assertEquals("minecraft:air", model.stateAt(0, 4, 0));
        assertEquals("minecraft:chest[facing=north,type=single,waterlogged=false]", model.stateAt(7, 4, 3));
        assertEquals(Set.of(new SchematicModel.Pos(7, 4, 3)), model.blockEntities().keySet());
        assertEquals(4671, model.dataVersion());
        assertEquals(24 * 4 * 5 + 1, model.blockTotal());
    }

    @Test
    void sections_split_at_section_borders() {
        FakeWorld world = new FakeWorld();
        CaptureSnapshot snapshot = new CaptureSnapshot(BOX, world, null);

        assertEquals(3, snapshot.sections());
        snapshot.step(Long.MAX_VALUE);
        assertEquals(-4, world.requests.get(0)[0]);
        assertEquals(-1, world.requests.get(0)[3]);
        assertEquals(0, world.requests.get(1)[0]);
        assertEquals(15, world.requests.get(1)[3]);
        assertEquals(16, world.requests.get(2)[0]);
        assertEquals(19, world.requests.get(2)[3]);
    }

    @Test
    void the_budget_spreads_sections_over_steps_but_always_reads_one() {
        FakeWorld world = new FakeWorld();
        CaptureSnapshot snapshot = new CaptureSnapshot(BOX, world, null);

        assertEquals(CaptureSnapshot.Outcome.RUNNING, snapshot.step(1));
        assertEquals(1, world.requests.size());
        assertEquals(4.0 * 8 * 5 / (24 * 8 * 5), snapshot.progress(), 1e-9);
        assertEquals(CaptureSnapshot.Outcome.RUNNING, snapshot.step(16 * 8 * 5));
        assertEquals(2, world.requests.size());
        assertEquals(CaptureSnapshot.Outcome.DONE, snapshot.step(16 * 8 * 5));
        assertEquals(1.0, snapshot.progress());
    }

    @Test
    void waits_for_block_entities_that_answer_later() {
        FakeWorld world = new FakeWorld();
        world.answerLater = true;
        CaptureSnapshot snapshot = new CaptureSnapshot(new CaptureBox.Bounds(0, 64, 0, 8, 8, 8), world, null);

        assertEquals(CaptureSnapshot.Outcome.RUNNING, snapshot.step(Long.MAX_VALUE));
        assertThrows(IllegalStateException.class, snapshot::model);

        world.pending.forEach(request -> request.complete(Map.of()));

        assertEquals(CaptureSnapshot.Outcome.DONE, snapshot.step(Long.MAX_VALUE));
    }

    @Test
    void a_failed_block_entity_request_fails_the_capture() {
        CaptureSnapshot.World failing = new FakeWorld() {
            @Override
            public CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>> blockEntities(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                return CompletableFuture.failedFuture(new IllegalStateException("server gone"));
            }
        };

        assertEquals(CaptureSnapshot.Outcome.FAILED, new CaptureSnapshot(new CaptureBox.Bounds(0, 60, 0, 4, 4, 4), failing, null).step(Long.MAX_VALUE));
    }

    @Test
    void an_unloaded_section_fails_the_capture() {
        FakeWorld world = new FakeWorld();
        CaptureSnapshot snapshot = new CaptureSnapshot(BOX, world, null);
        snapshot.step(1);

        world.unload(0, 0);

        assertEquals(CaptureSnapshot.Outcome.UNLOADED, snapshot.step(Long.MAX_VALUE));
        assertEquals(CaptureSnapshot.Outcome.UNLOADED, snapshot.step(Long.MAX_VALUE));
    }

    @Test
    void the_loaded_check_covers_every_chunk_column() {
        FakeWorld world = new FakeWorld();
        assertTrue(CaptureSnapshot.loaded(BOX, world));

        world.unload(1, 0);

        assertFalse(CaptureSnapshot.loaded(BOX, world));
    }
}

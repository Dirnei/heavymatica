package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.HeavymaticaConfig;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The comparisons of the locked placements in the player's dimension, and the scanner that keeps them current. Each
 * tick it rechecks every position within reach of the player, then spends the configured budget on whole sections:
 * those within {@value #NEAR} blocks get what one pass every {@value #NEAR_PASS_TICKS} ticks needs, at most three
 * quarters, nearest first; the rest goes to the others, sections never compared or last found unloaded first. Game
 * thread only.
 */
final class Comparisons implements AutoCloseable {

    static final int REACH = 6;
    static final int NEAR = 32;
    private static final double RESORT_DISTANCE = 8.0;
    private static final int NEAR_PASS_TICKS = 60;

    private static final class Entry {

        final WorldComparison comparison;
        int[] near = new int[0];
        int[] far = new int[0];
        int nearCells;
        int nearCursor;
        int farCursor;
        @Nullable Vec3 sortedAt;

        Entry(WorldComparison comparison) {
            this.comparison = comparison;
        }
    }

    private final Map<UUID, Entry> active = new HashMap<>();
    private final CellKeys keys = new CellKeys();
    private long slowestTickNanos;

    /** The placement's comparison, or null while it is not locked or its version is not loaded. */
    @Nullable WorldComparison get(Placement placement) {
        Entry entry = active.get(placement.id());
        return entry != null && entry.comparison.matches(placement) ? entry.comparison : null;
    }

    int size() {
        return active.size();
    }

    /** The longest tick since the last call, in nanoseconds, for development checks. */
    long takeSlowestTickNanos() {
        long slowest = slowestTickNanos;
        slowestTickNanos = 0;
        return slowest;
    }

    /** Creates and drops comparisons to follow the placements, then scans. */
    void tick(Collection<Placement> placements, Function<Placement, @Nullable SchematicModel> models) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            close();
            return;
        }
        String dimension = level.dimension().identifier().toString();
        Set<UUID> live = new HashSet<>();
        for (Placement placement : placements) {
            SchematicModel model = models.apply(placement);
            if (!placement.locked() || !placement.dimension().equals(dimension) || model == null) {
                continue;
            }
            live.add(placement.id());
            Entry entry = active.get(placement.id());
            if (entry == null || !entry.comparison.matches(placement)) {
                if (entry != null) {
                    entry.comparison.close();
                }
                active.put(placement.id(), new Entry(new WorldComparison(placement, model, keys)));
            }
        }
        active.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().comparison.close();
            return true;
        });
        if (active.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        WorldComparison.ChunkReader chunks = chunks(level);
        List<Entry> smallestFirst = new ArrayList<>(active.values());
        smallestFirst.sort(Comparator.comparingInt(entry -> entry.comparison.state().sectionCount()));
        int left = HeavymaticaConfig.comparisonCellsPerTick();
        for (int i = 0; i < smallestFirst.size(); i++) {
            left -= scan(smallestFirst.get(i), player, chunks, left / (smallestFirst.size() - i));
        }
        slowestTickNanos = Math.max(slowestTickNanos, System.nanoTime() - start);
    }

    /** Scans one comparison within {@code budget} cells, besides the reach zone; returns the cells used. */
    private int scan(Entry entry, LocalPlayer player, WorldComparison.ChunkReader chunks, int budget) {
        WorldComparison comparison = entry.comparison;
        ComparisonState state = comparison.state();
        reach(comparison, player.getEyePosition(), chunks);

        Vec3 at = player.position();
        if (entry.sortedAt == null || entry.sortedAt.distanceTo(at) > RESORT_DISTANCE) {
            sort(entry, at);
        }
        int used = 0;
        int nearBudget = entry.far.length == 0 ? budget : Math.min(budget * 3 / 4, Math.ceilDiv(entry.nearCells, NEAR_PASS_TICKS));
        for (int i = 0; i < entry.near.length && used < nearBudget; i++) {
            entry.nearCursor = (entry.nearCursor + 1) % entry.near.length;
            used += comparison.compareSection(entry.near[entry.nearCursor], chunks, keys);
        }
        for (int i = 0; i < entry.far.length && used < budget; i++) {
            if (state.needsScan(entry.far[i])) {
                used += comparison.compareSection(entry.far[i], chunks, keys);
            }
        }
        for (int i = 0; i < entry.far.length && used < budget; i++) {
            entry.farCursor = (entry.farCursor + 1) % entry.far.length;
            used += comparison.compareSection(entry.far[entry.farCursor], chunks, keys);
        }
        return used;
    }

    /** Rechecks every position within {@value #REACH} blocks of the eyes, so the player's own changes show at once. */
    private void reach(WorldComparison comparison, Vec3 eye, WorldComparison.ChunkReader chunks) {
        ComparisonState state = comparison.state();
        Bounds bounds = comparison.bounds();
        int x0 = Math.max(0, (int) Math.floor(eye.x) - REACH - bounds.minX());
        int y0 = Math.max(0, (int) Math.floor(eye.y) - REACH - bounds.minY());
        int z0 = Math.max(0, (int) Math.floor(eye.z) - REACH - bounds.minZ());
        int x1 = Math.min(state.sizeX() - 1, (int) Math.floor(eye.x) + REACH - bounds.minX());
        int y1 = Math.min(state.sizeY() - 1, (int) Math.floor(eye.y) + REACH - bounds.minY());
        int z1 = Math.min(state.sizeZ() - 1, (int) Math.floor(eye.z) + REACH - bounds.minZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                LevelChunk chunk = chunks.chunk(bounds.minX() + x, bounds.minZ() + z);
                if (chunk == null) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    comparison.compare(x, y, z, chunk.getBlockState(pos.set(bounds.minX() + x, bounds.minY() + y, bounds.minZ() + z)), keys);
                }
            }
        }
    }

    /** Splits the sections into near and far, each ordered by distance from the player. */
    private static void sort(Entry entry, Vec3 at) {
        ComparisonState state = entry.comparison.state();
        Bounds bounds = entry.comparison.bounds();
        double[] distance = new double[state.sectionCount()];
        List<Integer> near = new ArrayList<>();
        List<Integer> far = new ArrayList<>();
        int nearCells = 0;
        for (int section = 0; section < distance.length; section++) {
            int[] min = state.sectionMin(section);
            distance[section] = Math.sqrt(squared(at.x, bounds.minX() + min[0], Math.min(state.sizeX(), min[0] + ComparisonState.SECTION) + bounds.minX())
                    + squared(at.y, bounds.minY() + min[1], Math.min(state.sizeY(), min[1] + ComparisonState.SECTION) + bounds.minY())
                    + squared(at.z, bounds.minZ() + min[2], Math.min(state.sizeZ(), min[2] + ComparisonState.SECTION) + bounds.minZ()));
            if (distance[section] <= NEAR) {
                near.add(section);
                nearCells += (Math.min(state.sizeX(), min[0] + ComparisonState.SECTION) - min[0])
                        * (Math.min(state.sizeY(), min[1] + ComparisonState.SECTION) - min[1])
                        * (Math.min(state.sizeZ(), min[2] + ComparisonState.SECTION) - min[2]);
            } else {
                far.add(section);
            }
        }
        Comparator<Integer> closest = Comparator.comparingDouble(section -> distance[section]);
        near.sort(closest);
        far.sort(closest);
        entry.near = near.stream().mapToInt(Integer::intValue).toArray();
        entry.far = far.stream().mapToInt(Integer::intValue).toArray();
        entry.nearCursor = entry.near.length - 1;
        entry.farCursor = entry.far.length - 1;
        entry.nearCells = nearCells;
        entry.sortedAt = at;
    }

    /** Squared distance from {@code value} to the range {@code [min, max]} on one axis. */
    private static double squared(double value, double min, double max) {
        double outside = value < min ? min - value : value > max ? value - max : 0.0;
        return outside * outside;
    }

    private static WorldComparison.ChunkReader chunks(ClientLevel level) {
        return new WorldComparison.ChunkReader() {
            private long cachedKey = Long.MIN_VALUE;
            private @Nullable LevelChunk cached;

            @Override
            public @Nullable LevelChunk chunk(int blockX, int blockZ) {
                int chunkX = blockX >> 4;
                int chunkZ = blockZ >> 4;
                long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
                if (key != cachedKey) {
                    cachedKey = key;
                    // The client level claims to have every chunk; only the chunk cache knows which are loaded.
                    cached = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                }
                return cached;
            }
        };
    }

    @Override
    public void close() {
        active.values().forEach(entry -> entry.comparison.close());
        active.clear();
    }
}

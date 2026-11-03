package io.github.dirnei.mc.heavymatica.capture;

import io.github.dirnei.mc.heavymatica.formats.Nbt;
import io.github.dirnei.mc.heavymatica.formats.SchematicFormat;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * Copies a box of the world into a model, one 16 x 16 x 16 section at a time and within a budget of cells per step,
 * so a large box never stalls a tick. A section is read in one go. Block entities may arrive later than their
 * section; the snapshot is done once every section is read and every block entity request has answered.
 */
public final class CaptureSnapshot {

    /** The world as the snapshot sees it; called on the thread that steps the snapshot. */
    public interface World {

        boolean loaded(int chunkX, int chunkZ);

        /** The block state at a position, as an object whose equality means the same state. */
        Object state(int x, int y, int z);

        /** A state's canonical block-state string. */
        String name(Object state);

        /** The block entities in a box, both corners included, by world position, without their position keys. */
        CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>> blockEntities(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    }

    public enum Outcome {
        RUNNING,
        DONE,
        UNLOADED,
        FAILED,
    }

    private record Section(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        long cells() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private final CaptureBox.Bounds bounds;
    private final World world;
    private final @Nullable Integer dataVersion;
    private final List<Section> sections = new ArrayList<>();
    private final int[] blocks;
    private final List<String> palette = new ArrayList<>();
    private final Map<Object, Integer> paletteIndex = new HashMap<>();
    private final List<CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>>> blockEntityRequests = new ArrayList<>();
    private int next;
    private long cellsRead;
    private Outcome outcome = Outcome.RUNNING;

    public CaptureSnapshot(CaptureBox.Bounds bounds, World world, @Nullable Integer dataVersion) {
        this.bounds = bounds;
        this.world = world;
        this.dataVersion = dataVersion;
        this.blocks = new int[Math.toIntExact(bounds.volume())];
        for (int sy = Math.floorDiv(bounds.minY(), 16); sy <= Math.floorDiv(bounds.maxY(), 16); sy++) {
            for (int sz = Math.floorDiv(bounds.minZ(), 16); sz <= Math.floorDiv(bounds.maxZ(), 16); sz++) {
                for (int sx = Math.floorDiv(bounds.minX(), 16); sx <= Math.floorDiv(bounds.maxX(), 16); sx++) {
                    sections.add(new Section(Math.max(bounds.minX(), sx * 16), Math.max(bounds.minY(), sy * 16), Math.max(bounds.minZ(), sz * 16),
                            Math.min(bounds.maxX(), sx * 16 + 15), Math.min(bounds.maxY(), sy * 16 + 15), Math.min(bounds.maxZ(), sz * 16 + 15)));
                }
            }
        }
    }

    /** True when every chunk column the box touches is loaded. */
    public static boolean loaded(CaptureBox.Bounds bounds, World world) {
        for (int cx = Math.floorDiv(bounds.minX(), 16); cx <= Math.floorDiv(bounds.maxX(), 16); cx++) {
            for (int cz = Math.floorDiv(bounds.minZ(), 16); cz <= Math.floorDiv(bounds.maxZ(), 16); cz++) {
                if (!world.loaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    public int sections() {
        return sections.size();
    }

    /** The share of cells read, from 0 to 1. */
    public double progress() {
        return blocks.length == 0 ? 1 : (double) cellsRead / blocks.length;
    }

    /** Reads whole sections until {@code budget} cells are used, at least one section. */
    public Outcome step(long budget) {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        long used = 0;
        while (next < sections.size() && (used == 0 || used + sections.get(next).cells() <= budget)) {
            Section section = sections.get(next);
            if (!world.loaded(Math.floorDiv(section.minX(), 16), Math.floorDiv(section.minZ(), 16))) {
                outcome = Outcome.UNLOADED;
                return outcome;
            }
            read(section);
            used += section.cells();
            next++;
        }
        if (next < sections.size()) {
            return outcome;
        }
        for (CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>> request : blockEntityRequests) {
            if (!request.isDone()) {
                return outcome;
            }
            if (request.isCompletedExceptionally()) {
                outcome = Outcome.FAILED;
                return outcome;
            }
        }
        outcome = Outcome.DONE;
        return outcome;
    }

    /** The captured model; only once {@link #step} returned DONE. */
    public SchematicModel model() {
        if (outcome != Outcome.DONE) {
            throw new IllegalStateException("The snapshot is not done.");
        }
        Map<SchematicModel.Pos, Nbt.Compound> blockEntities = new HashMap<>();
        for (CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>> request : blockEntityRequests) {
            request.join().forEach((pos, tag) -> blockEntities.put(
                    new SchematicModel.Pos(pos.x() - bounds.minX(), pos.y() - bounds.minY(), pos.z() - bounds.minZ()), tag));
        }
        return new SchematicModel(SchematicFormat.LITEMATICA, bounds.width(), bounds.height(), bounds.length(), palette, blocks, blockEntities,
                dataVersion, 0);
    }

    private void read(Section section) {
        int width = bounds.width(), length = bounds.length();
        for (int y = section.minY(); y <= section.maxY(); y++) {
            for (int z = section.minZ(); z <= section.maxZ(); z++) {
                for (int x = section.minX(); x <= section.maxX(); x++) {
                    Object state = world.state(x, y, z);
                    Integer index = paletteIndex.get(state);
                    if (index == null) {
                        palette.add(world.name(state));
                        index = palette.size() - 1;
                        paletteIndex.put(state, index);
                    }
                    blocks[SchematicModel.index(x - bounds.minX(), y - bounds.minY(), z - bounds.minZ(), width, length)] = index;
                }
            }
        }
        cellsRead += section.cells();
        blockEntityRequests.add(world.blockEntities(section.minX(), section.minY(), section.minZ(), section.maxX(), section.maxY(), section.maxZ()));
    }
}

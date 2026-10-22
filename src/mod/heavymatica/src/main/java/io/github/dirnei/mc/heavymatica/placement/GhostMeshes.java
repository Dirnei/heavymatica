package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.Placement.Flip;
import io.github.dirnei.mc.heavymatica.placement.Placement.Turn;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Ghost meshes by (content hash, swaps, rotation, mirror). Building runs on a worker; until it is done a placement keeps
 * drawing the mesh it showed before. Meshes no live placement needs are freed, and everything is dropped when block
 * models are reloaded. Render thread only.
 */
final class GhostMeshes implements AutoCloseable {

    record Key(String sha256, Map<String, String> swaps, Turn rotation, Flip mirror) {

        static Key of(Placement placement) {
            return new Key(placement.source().sha256(), placement.swaps(), placement.rotation(), placement.mirror());
        }

        GhostView view(SchematicModel model) {
            return new GhostView(model, rotation, mirror, swaps);
        }
    }

    private final Map<Key, GhostMesh> ready = new HashMap<>();
    private final Map<Key, CompletableFuture<GhostMesh.Built>> building = new HashMap<>();
    private final Map<UUID, Key> shown = new HashMap<>();
    private final Map<Key, GhostCells> readyCells = new HashMap<>();
    private final Map<Key, CompletableFuture<GhostCells.Built>> buildingCells = new HashMap<>();
    private @Nullable Object modelSet;

    /** The mesh to draw for a placement, starting a build when needed; null while there is nothing to show yet. */
    @Nullable GhostMesh mesh(Placement placement, SchematicModel model) {
        Key key = Key.of(placement);
        GhostMesh mesh = ready.get(key);
        if (mesh == null) {
            CompletableFuture<GhostMesh.Built> build = building.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
                GhostView view = key.view(model);
                return GhostMeshBuilder.build(view, view.sizeX(), view.sizeY(), view.sizeZ());
            }, Util.backgroundExecutor()));
            if (build.isDone()) {
                building.remove(key);
                if (build.isCompletedExceptionally()) {
                    build.exceptionally(failure -> {
                        Heavymatica.LOGGER.error("Ghost mesh for {} failed", key, failure);
                        return null;
                    });
                } else {
                    mesh = GhostMesh.upload(build.join());
                    ready.put(key, mesh);
                }
            }
        }
        if (mesh != null) {
            shown.put(placement.id(), key);
            return mesh;
        }
        Key previous = shown.get(placement.id());
        return previous == null ? null : ready.get(previous);
    }

    /** True when the placement draws from cells: every locked placement, since cells can hide what is already built. */
    static boolean usesCells(Placement placement) {
        return placement.locked();
    }

    /** The placement's cells, starting a build when needed; null until they are ready. */
    @Nullable GhostCells cells(Placement placement, SchematicModel model) {
        Key key = Key.of(placement);
        GhostCells cells = readyCells.get(key);
        if (cells != null) {
            return cells;
        }
        CompletableFuture<GhostCells.Built> build = buildingCells.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(
                () -> GhostCells.build(key.view(model)), Util.backgroundExecutor()));
        if (!build.isDone()) {
            return null;
        }
        buildingCells.remove(key);
        if (build.isCompletedExceptionally()) {
            build.exceptionally(failure -> {
                Heavymatica.LOGGER.error("Ghost cells for {} failed", key, failure);
                return null;
            });
            return null;
        }
        cells = GhostCells.upload(build.join());
        readyCells.put(key, cells);
        return cells;
    }

    /** Frees meshes no live placement needs; drops everything after a model reload. */
    void sweep(Collection<Placement> live, Function<Placement, @Nullable SchematicModel> models) {
        Object current = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        if (modelSet != current) {
            if (modelSet != null) {
                Heavymatica.LOGGER.info("Block models changed; rebuilding ghost meshes");
            }
            modelSet = current;
            close();
            return;
        }
        Set<Key> needed = new HashSet<>();
        Set<Key> neededCells = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (Placement placement : live) {
            ids.add(placement.id());
            if (models.apply(placement) != null) {
                needed.add(Key.of(placement));
                if (usesCells(placement)) {
                    neededCells.add(Key.of(placement));
                }
            }
            Key previous = shown.get(placement.id());
            if (previous != null) {
                needed.add(previous);
            }
        }
        shown.keySet().retainAll(ids);
        int before = size();
        readyCells.entrySet().removeIf(entry -> {
            if (neededCells.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
        buildingCells.entrySet().removeIf(entry -> {
            if (neededCells.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().thenAccept(GhostCells.Built::close);
            return true;
        });
        ready.entrySet().removeIf(entry -> {
            if (needed.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
        building.entrySet().removeIf(entry -> {
            if (needed.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().thenAccept(GhostMesh.Built::close);
            return true;
        });
        if (size() != before) {
            Heavymatica.LOGGER.info("Freed {} ghost meshes, {} left", before - size(), size());
        }
    }

    /** Full meshes plus cell sets. */
    int size() {
        return ready.size() + readyCells.size();
    }

    @Override
    public void close() {
        ready.values().forEach(GhostMesh::close);
        ready.clear();
        building.values().forEach(build -> build.thenAccept(GhostMesh.Built::close));
        building.clear();
        readyCells.values().forEach(GhostCells::close);
        readyCells.clear();
        buildingCells.values().forEach(build -> build.thenAccept(GhostCells.Built::close));
        buildingCells.clear();
        shown.clear();
    }
}

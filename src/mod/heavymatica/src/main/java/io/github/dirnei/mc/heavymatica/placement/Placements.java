package io.github.dirnei.mc.heavymatica.placement;

import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * The placements of the current world or server and the one selected placement. Kept most recently selected or created
 * first, which is also the order the rendering budget is spent in. Not thread-safe: used on the game thread only.
 */
public final class Placements {

    /** A line of sight: an eye position and a direction (any length). */
    public record Ray(double x, double y, double z, double dx, double dy, double dz) {
    }

    /** How far the remote reaches, for selecting and following. */
    public static final double REACH = 64.0;

    private final List<Placement> placements = new ArrayList<>();
    private final List<Consumer<Placements>> listeners = new CopyOnWriteArrayList<>();
    private @Nullable UUID selected;

    /** Subscribes to changes; run the returned handle to unsubscribe. */
    public Runnable onChange(Consumer<Placements> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Every placement, most recently selected or created first. */
    public List<Placement> all() {
        return List.copyOf(placements);
    }

    public List<Placement> inDimension(String dimension) {
        return placements.stream().filter(placement -> placement.dimension().equals(dimension)).toList();
    }

    public Optional<Placement> get(UUID id) {
        return placements.stream().filter(placement -> placement.id().equals(id)).findFirst();
    }

    public Optional<Placement> selected() {
        return selected == null ? Optional.empty() : get(selected);
    }

    /** Adds a placement and selects it. */
    public void add(Placement placement) {
        placements.addFirst(placement);
        selected = placement.id();
        changed();
    }

    /** Replaces the placement with the same ID; unknown IDs are ignored. */
    public void update(Placement placement) {
        for (int i = 0; i < placements.size(); i++) {
            if (placements.get(i).id().equals(placement.id())) {
                if (!placements.get(i).equals(placement)) {
                    placements.set(i, placement);
                    changed();
                }
                return;
            }
        }
    }

    public void select(UUID id) {
        get(id).ifPresent(placement -> {
            placements.remove(placement);
            placements.addFirst(placement);
            selected = id;
            changed();
        });
    }

    public void remove(UUID id) {
        if (placements.removeIf(placement -> placement.id().equals(id))) {
            if (id.equals(selected)) {
                selected = null;
            }
            changed();
        }
    }

    /** Swaps in a loaded set, nothing selected. */
    public void replaceAll(List<Placement> loaded) {
        placements.clear();
        placements.addAll(loaded);
        selected = null;
        changed();
    }

    /**
     * The placement in {@code dimension} whose box the ray enters first, within {@link #REACH}. A box the eye is inside
     * counts as entered at distance 0; between equally near boxes the smallest wins.
     */
    public Optional<Placement> firstHit(String dimension, Ray ray) {
        Placement best = null;
        double bestDistance = Double.MAX_VALUE;
        double length = Math.sqrt(ray.dx() * ray.dx() + ray.dy() * ray.dy() + ray.dz() * ray.dz());
        if (length == 0.0) {
            return Optional.empty();
        }
        for (Placement placement : placements) {
            if (!placement.dimension().equals(dimension)) {
                continue;
            }
            double distance = entry(PlacementTransform.bounds(placement), ray, length);
            if (distance < 0.0) {
                continue;
            }
            if (distance < bestDistance || distance == bestDistance && PlacementTransform.bounds(placement).volume() < PlacementTransform.bounds(best).volume()) {
                best = placement;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Slab test: distance along the ray where it enters the box, 0 when it starts inside, -1 when it misses within reach. */
    static double entry(Bounds box, Ray ray, double length) {
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;
        double[] origin = {ray.x(), ray.y(), ray.z()};
        double[] direction = {ray.dx() / length, ray.dy() / length, ray.dz() / length};
        double[] min = {box.minX(), box.minY(), box.minZ()};
        double[] max = {box.maxX(), box.maxY(), box.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (direction[axis] == 0.0) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
                    return -1.0;
                }
                continue;
            }
            double t1 = (min[axis] - origin[axis]) / direction[axis];
            double t2 = (max[axis] - origin[axis]) / direction[axis];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
        }
        if (near > far || far < 0.0) {
            return -1.0;
        }
        double distance = Math.max(near, 0.0);
        return distance <= REACH ? distance : -1.0;
    }

    private void changed() {
        listeners.forEach(listener -> listener.accept(this));
    }
}

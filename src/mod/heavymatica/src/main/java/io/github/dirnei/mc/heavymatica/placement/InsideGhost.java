package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.dirnei.mc.heavymatica.blocks.BlockGeometry;
import io.github.dirnei.mc.heavymatica.blocks.SpecialGeometry;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * The faces a ghost mesh leaves out because a neighbouring ghost covers them, for the ghost cell the camera is in: the
 * faces of its six neighbours that point at it. From inside a ghost the camera only sees back faces, so without these
 * a wall of ghosts vanishes around the player. Built on the render thread when the camera enters another cell. Render
 * thread only.
 */
final class InsideGhost implements AutoCloseable {

    /** The opacity of a neighbouring cell, 0 when it shows no ghost. */
    @FunctionalInterface
    interface CellAlpha {
        float alpha(int x, int y, int z, BlockState planned);
    }

    private record Key(GhostMeshes.Key mesh, Object models, int x, int y, int z, int visible) {
    }

    private record Face(GhostMesh mesh, float alpha) {
    }

    private record Entry(Key key, List<GhostMesh> meshes) {
    }

    private record View(GhostMeshes.Key key, SchematicModel model, GhostView view) {
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();

    /**
     * Draws the uncovered neighbour faces of the cell the camera is in, if that cell is inside the placement and plans a
     * block.
     *
     * @param sameLayerOnly true when the placement's mesh culls a block only against the same block in the same layer
     */
    void draw(Placement placement, SchematicModel model, Bounds bounds, Vec3 camera, Matrix4fc modelView, Vector3f offset, boolean sameLayerOnly,
            Predicate<BlockState> eyeShown, CellAlpha alpha) {
        int ex = (int) Math.floor(camera.x - bounds.minX());
        int ey = (int) Math.floor(camera.y - bounds.minY());
        int ez = (int) Math.floor(camera.z - bounds.minZ());
        if (ex < 0 || ey < 0 || ez < 0 || ex >= bounds.sizeX() || ey >= bounds.sizeY() || ez >= bounds.sizeZ()) {
            return;
        }
        GhostMeshes.Key meshKey = GhostMeshes.Key.of(placement);
        GhostView view = views.compute(placement.id(), (id, old) -> old != null && old.model() == model && old.key().equals(meshKey) ? old
                : new View(meshKey, model, GhostView.of(model, placement))).view();
        BlockPos eye = new BlockPos(ex, ey, ez);
        BlockState eyeState = view.getBlockState(eye);
        if (eyeState.isAir() || !eyeShown.test(eyeState)) {
            return;
        }
        float[] alphas = new float[6];
        int visible = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = eye.relative(direction);
            if (!inside(view, neighbour) || view.unknownAt(neighbour.getX(), neighbour.getY(), neighbour.getZ())) {
                continue;
            }
            BlockState state = view.getBlockState(neighbour);
            if (state.isAir()) {
                continue;
            }
            float value = alpha.alpha(neighbour.getX(), neighbour.getY(), neighbour.getZ(), state);
            if (value > 0.0F) {
                alphas[direction.ordinal()] = value;
                visible |= 1 << direction.ordinal();
            }
        }
        if (visible == 0) {
            return;
        }
        Key key = new Key(meshKey, Minecraft.getInstance().getModelManager().getBlockStateModelSet(), ex, ey, ez, visible);
        Entry entry = entries.get(placement.id());
        if (entry == null || !entry.key().equals(key)) {
            if (entry != null) {
                close(entry.meshes());
            }
            entry = new Entry(key, build(view, eye, visible, sameLayerOnly));
            entries.put(placement.id(), entry);
        }
        List<Face> faces = new ArrayList<>();
        for (int i = 0, d = 0; d < 6; d++) {
            if ((visible & 1 << d) != 0) {
                GhostMesh mesh = entry.meshes().get(i++);
                if (mesh != null) {
                    faces.add(new Face(mesh, alphas[d]));
                }
            }
        }
        for (Face face : faces) {
            face.mesh().draw(modelView, offset, face.alpha(), 0.0F);
        }
    }

    /** Frees what belongs to placements no longer drawn. */
    void retain(Set<UUID> drawn) {
        for (Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Entry> entry = it.next();
            if (!drawn.contains(entry.getKey())) {
                close(entry.getValue().meshes());
                it.remove();
            }
        }
        views.keySet().retainAll(drawn);
    }

    @Override
    public void close() {
        entries.values().forEach(entry -> close(entry.meshes()));
        entries.clear();
        views.clear();
    }

    private static void close(List<GhostMesh> meshes) {
        for (GhostMesh mesh : meshes) {
            if (mesh != null) {
                mesh.close();
            }
        }
    }

    /** One mesh per visible neighbour, in direction order; null where nothing is uncovered. */
    private static List<GhostMesh> build(GhostView view, BlockPos eye, int visible, boolean sameLayerOnly) {
        SpecialGeometry.get().prepare(view.placedStates());
        BlockGeometry geometry = new BlockGeometry(SpecialGeometry.get());
        List<GhostMesh> meshes = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if ((visible & 1 << direction.ordinal()) == 0) {
                continue;
            }
            BlockPos neighbour = eye.relative(direction);
            BlockState state = view.getBlockState(neighbour);
            Overlay covered = new Overlay(view, sameLayerOnly ? state : null, neighbour.getY(), null);
            Overlay open = new Overlay(view, sameLayerOnly ? state : null, neighbour.getY(), eye);
            Map<Identifier, Buffer> buffers = new LinkedHashMap<>();
            boolean wrote;
            try {
                wrote = geometry.emitUncovered(covered, open, neighbour, state,
                        (texture, layer) -> buffers.computeIfAbsent(texture, Buffer::new).buffer);
            } catch (RuntimeException | Error e) {
                buffers.values().forEach(buffer -> buffer.bytes.close());
                throw e;
            }
            List<GhostMesh.BuiltTexture> textures = new ArrayList<>();
            for (Buffer buffer : buffers.values()) {
                textures.add(new GhostMesh.BuiltTexture(buffer.texture, buffer.bytes, buffer.buffer.build()));
            }
            GhostMesh.Built built = new GhostMesh.Built(textures, null, 1);
            if (wrote) {
                meshes.add(GhostMesh.upload(built));
            } else {
                built.close();
                meshes.add(null);
            }
        }
        return meshes;
    }

    private static boolean inside(GhostView view, BlockPos pos) {
        return pos.getX() >= 0 && pos.getY() >= 0 && pos.getZ() >= 0 && pos.getX() < view.sizeX() && pos.getY() < view.sizeY()
                && pos.getZ() < view.sizeZ();
    }

    private static final class Buffer {

        private final Identifier texture;
        private final ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 10);
        private final BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

        Buffer(Identifier texture) {
            this.texture = texture;
        }
    }

    /**
     * The placed view as a mesh culls against it: everything, or only blocks of {@code only}'s type in layer {@code y};
     * {@code hole} reads as air.
     */
    private static final class Overlay implements BlockAndTintGetter {

        private final GhostView view;
        private final @Nullable BlockState only;
        private final int y;
        private final @Nullable BlockPos hole;

        Overlay(GhostView view, @Nullable BlockState only, int y, @Nullable BlockPos hole) {
            this.view = view;
            this.only = only;
            this.y = y;
            this.hole = hole;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.equals(hole) || !inside(view, pos)) {
                return Blocks.AIR.defaultBlockState();
            }
            BlockState state = view.getBlockState(pos);
            if (only != null && (pos.getY() != y || !state.is(only.getBlock()))) {
                return Blocks.AIR.defaultBlockState();
            }
            return state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return view.getHeight();
        }

        @Override
        public int getMinY() {
            return 0;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return view.cardinalLighting();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return view.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening) {
            return 15;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver color) {
            return view.getBlockTint(pos, color);
        }
    }
}

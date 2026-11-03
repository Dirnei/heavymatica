package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jetbrains.annotations.Nullable;

/**
 * The coloured boxes over mistakes and blocks that need finishing of one comparison: one vertex buffer per 16-block
 * section with its quads ordered by layer, rebuilt when the scanner changes a boxed state or the block in a boxed cell.
 * A box follows the outline of the block found there. Faces between two full boxes of the same colour are left out, so a
 * large run of mistakes (terrain or water inside a placement) costs its surface, not its volume.
 * Render thread only.
 */
final class MistakeBoxes implements AutoCloseable {

    static final int WRONG_BLOCK_COLOR = 0x70FF2020;
    static final int WRONG_ORIENTATION_COLOR = 0x70FF9020;
    static final int WRONG_WATER_COLOR = 0x7020E0F0;
    static final int EXTRA_COLOR = 0x70FF30FF;
    static final int NEEDS_FINISHING_COLOR = 0x70FFF020;

    private static final float INFLATE = 0.005F;
    private static final int INDICES_PER_QUAD = 6;

    /** One section's boxes; {@code layerStart[y - section min y]} is the first quad of that layer, the last entry the total. */
    private record Section(int minY, GpuBuffer vertices, int[] layerStart) {
    }

    private record Draw(GpuBuffer vertices, int firstQuad, int endQuad) {
    }

    /** The outline of the block found at a local position. */
    interface CellShapes {

        /** The parts of the found block's outline in cell units, or null when the block fills its cell or has no outline. */
        @Nullable List<AABB> at(int x, int y, int z);
    }

    private final ComparisonState state;
    private final CellShapes shapes;
    private final Map<Integer, Section> sections = new HashMap<>();

    MistakeBoxes(ComparisonState state, CellShapes shapes) {
        this.state = state;
        this.shapes = shapes;
    }

    static int color(CellState cell) {
        return switch (cell) {
            case WRONG_BLOCK -> WRONG_BLOCK_COLOR;
            case WRONG_ORIENTATION, TOO_MANY, INCOMPLETE -> WRONG_ORIENTATION_COLOR;
            case WRONG_WATER -> WRONG_WATER_COLOR;
            case EXTRA -> EXTRA_COLOR;
            case NEEDS_FINISHING -> NEEDS_FINISHING_COLOR;
            default -> 0;
        };
    }

    /** Rebuilds the sections whose boxes changed. */
    void update() {
        BitSet dirty = state.takeDirtyBoxSections();
        for (int section = dirty.nextSetBit(0); section >= 0; section = dirty.nextSetBit(section + 1)) {
            Section old = sections.remove(section);
            if (old != null) {
                old.vertices().close();
            }
            Section built = build(section);
            if (built != null) {
                sections.put(section, built);
            }
        }
    }

    private @Nullable Section build(int section) {
        int[] min = state.sectionMin(section);
        int maxX = Math.min(min[0] + ComparisonState.SECTION, state.sizeX());
        int maxY = Math.min(min[1] + ComparisonState.SECTION, state.sizeY());
        int maxZ = Math.min(min[2] + ComparisonState.SECTION, state.sizeZ());
        int[] layerStart = new int[maxY - min[1] + 1];
        int quads = 0;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 12)) {
            BufferBuilder buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int y = min[1]; y < maxY; y++) {
                layerStart[y - min[1]] = quads;
                for (int z = min[2]; z < maxZ; z++) {
                    for (int x = min[0]; x < maxX; x++) {
                        CellState cell = state.state(state.index(x, y, z));
                        int color = color(cell);
                        if (color != 0) {
                            quads += box(buffer, x, y, z, color, cell);
                        }
                    }
                }
            }
            layerStart[layerStart.length - 1] = quads;
            if (quads == 0) {
                return null;
            }
            try (MeshData mesh = buffer.buildOrThrow()) {
                GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "Heavymatica mistake boxes", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                return new Section(min[1], vertices, layerStart);
            }
        }
    }

    /**
     * Adds one cell's box; returns how many faces. A block that fills its cell gets a cube without the faces it shares
     * with a full neighbour of the same state; any other block gets its own outline, one box per part.
     */
    private int box(BufferBuilder buffer, int x, int y, int z, int color, CellState cell) {
        List<AABB> parts = shapes.at(x, y, z);
        if (parts != null) {
            int faces = 0;
            for (AABB part : parts) {
                faces += cuboid(buffer, color, (float) (x + part.minX), (float) (y + part.minY), (float) (z + part.minZ),
                        (float) (x + part.maxX), (float) (y + part.maxY), (float) (z + part.maxZ), true, true, true, true, true, true);
            }
            return faces;
        }
        return cuboid(buffer, color, x, y, z, x + 1, y + 1, z + 1, open(x, y - 1, z, cell), open(x, y + 1, z, cell),
                open(x, y, z - 1, cell), open(x, y, z + 1, cell), open(x - 1, y, z, cell), open(x + 1, y, z, cell));
    }

    /** Whether a full box needs its face towards this neighbour: not when the neighbour is a full box of the same state. */
    private boolean open(int x, int y, int z, CellState cell) {
        return state.stateOrUnknown(x, y, z) != cell || shapes.at(x, y, z) != null;
    }

    private static int cuboid(BufferBuilder buffer, int color, float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
            boolean down, boolean up, boolean north, boolean south, boolean west, boolean east) {
        float x0 = minX - INFLATE;
        float y0 = minY - INFLATE;
        float z0 = minZ - INFLATE;
        float x1 = maxX + INFLATE;
        float y1 = maxY + INFLATE;
        float z1 = maxZ + INFLATE;
        int faces = 0;
        if (down) {
            quad(buffer, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            faces++;
        }
        if (up) {
            quad(buffer, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            faces++;
        }
        if (north) {
            quad(buffer, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            faces++;
        }
        if (south) {
            quad(buffer, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            faces++;
        }
        if (west) {
            quad(buffer, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            faces++;
        }
        if (east) {
            quad(buffer, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
            faces++;
        }
        return faces;
    }

    private static void quad(BufferBuilder buffer, int color, float... corners) {
        for (int i = 0; i < 12; i += 3) {
            buffer.addVertex(corners[i], corners[i + 1], corners[i + 2]).setColor(color);
        }
    }

    /** Draws the boxes of layers {@code from} to {@code to} at {@code offset} (placement minimum corner minus camera). */
    void draw(Matrix4fc modelView, Vector3f offset, int from, int to) {
        List<Draw> draws = new ArrayList<>();
        int maxQuad = 0;
        for (Section section : sections.values()) {
            int last = section.layerStart().length - 2;
            int low = Math.max(0, from - section.minY());
            int high = Math.min(last, to - section.minY());
            if (low > high) {
                continue;
            }
            int first = section.layerStart()[low];
            int end = section.layerStart()[high + 1];
            if (end > first) {
                draws.add(new Draw(section.vertices(), first, end));
                maxQuad = Math.max(maxQuad, end);
            }
        }
        if (draws.isEmpty()) {
            return;
        }
        Matrix4f moved = new Matrix4f(modelView).translate(offset);
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(maxQuad * INDICES_PER_QUAD);
        drawPass(GhostPipeline.MISTAKE_BOX, 1.0F, moved, indices, indexBuffer, draws);
    }

    private static void drawPass(RenderPipeline pipeline, float alpha, Matrix4f moved, RenderSystem.AutoStorageIndexBuffer indices,
            GpuBuffer indexBuffer, List<Draw> draws) {
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
                .writeTransform(moved, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), new Matrix4f());
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Heavymatica mistake boxes",
                target.getColorTextureView(), OptionalInt.empty(), target.useDepth ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            pass.setIndexBuffer(indexBuffer, indices.type());
            for (Draw draw : draws) {
                pass.setVertexBuffer(0, draw.vertices());
                pass.drawIndexed(0, draw.firstQuad() * INDICES_PER_QUAD, (draw.endQuad() - draw.firstQuad()) * INDICES_PER_QUAD, 1);
            }
        }
    }

    @Override
    public void close() {
        sections.values().forEach(section -> section.vertices().close());
        sections.clear();
    }
}

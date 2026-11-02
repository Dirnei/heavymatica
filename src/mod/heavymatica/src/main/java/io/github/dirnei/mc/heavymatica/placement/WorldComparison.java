package io.github.dirnei.mc.heavymatica.placement;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.placement.CellState.Expected;
import io.github.dirnei.mc.heavymatica.placement.PlacementTransform.Bounds;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * One locked placement compared with the world: what each position should hold, the {@link ComparisonState}, the
 * {@link MaterialCounts} of the positions that still need items or finishing, and its GPU side (the ghost mask
 * and the boxes). Game and render thread, which are the same on the client.
 */
public final class WorldComparison implements AutoCloseable {

    private final Placement placement;
    private final Bounds bounds;
    private final GhostView view;
    private final Expected[] expected;
    private final Demand[] demands;
    private final int[] plannedOf;
    private final int[] contentOf;
    private final List<Item> items = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();
    private final List<Block> planned = new ArrayList<>();
    private final List<Integer> placingItems = new ArrayList<>();
    private final List<int[]> accepts = new ArrayList<>();
    private final List<BlockEquivalents.@Nullable Finish> finishes = new ArrayList<>();
    private final Map<Block, Set<Block>> highlights = new IdentityHashMap<>();
    private final MaterialCounts materials;
    private final ComparisonState state;
    private final MistakeBoxes boxes;
    /** The world block in each boxed cell by cell index, so a box follows another block taking its place in the same state. */
    private final Int2IntOpenHashMap boxedBlocks = new Int2IntOpenHashMap();
    private final UnknownCounts unknown;
    private final CellKeys keys;
    private final int[] scratch = new int[2];
    private @Nullable GpuBuffer mask;

    WorldComparison(Placement placement, SchematicModel model, CellKeys keys) {
        this.placement = placement;
        this.keys = keys;
        this.bounds = PlacementTransform.bounds(placement);
        this.view = new GhostView(model, placement.rotation(), placement.mirror(), placement.swaps());
        this.expected = new Expected[model.palette().size()];
        this.demands = new Demand[model.palette().size()];
        this.plannedOf = new int[model.palette().size()];
        this.contentOf = new int[model.palette().size()];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = keys.expected(view.entry(i), view.placed(i), view.unknown(i));
        }
        Map<Block, Integer> plannedIndex = indexPlanned(keys);
        Map<Block, Integer> blockIndex = new IdentityHashMap<>();
        for (int i = 0; i < expected.length; i++) {
            Block block = view.placed(i).getBlock();
            TwoItemBlocks.Parts parts = expected[i].counted() ? TwoItemBlocks.of(block) : null;
            Integer index = expected[i].counted() ? plannedIndex.get(parts == null ? block : parts.base()) : null;
            plannedOf[i] = index == null ? -1 : index;
            Integer content = parts == null ? null : plannedIndex.get(parts.content());
            contentOf[i] = content == null || index == null ? -1 : content;
            demands[i] = !expected[i].counted() ? Demand.NONE
                    : contentOf[i] >= 0 ? Demand.twoItem(plannedOf[i], contentOf[i]) : demand(view.placed(i), plannedOf[i], blockIndex);
        }
        this.materials = new MaterialCounts(view.sizeY(), planned.size(), blocks.size());
        this.unknown = new UnknownCounts(view.sizeY());
        BitSet counted = new BitSet();
        int index = 0;
        for (int y = 0; y < view.sizeY(); y++) {
            for (int z = 0; z < view.sizeZ(); z++) {
                for (int x = 0; x < view.sizeX(); x++, index++) {
                    int entry = view.paletteIndex(x, y, z, scratch);
                    if (expected[entry].counted()) {
                        counted.set(index);
                    } else if (expected[entry].kind() == Expected.Kind.CANT_CHECK) {
                        unknown.add(y, BlockStates.id(view.entry(entry)));
                    }
                    materials.add(y, demands[entry]);
                }
            }
        }
        this.state = new ComparisonState(view.sizeX(), view.sizeY(), view.sizeZ(), counted);
        this.boxes = new MistakeBoxes(state, this::foundOutline);
        boxedBlocks.defaultReturnValue(-1);
    }

    /**
     * Numbers the planned blocks an item places in block ID order, with their placing item, the items that count
     * toward them and their finishing step, and maps each block that counts toward a planned block to the planned
     * blocks it highlights. A two-item block is not numbered itself: its base and its content are, each with its own
     * item, and holding either highlights it. Returns the planned block indices.
     */
    private Map<Block, Integer> indexPlanned(CellKeys keys) {
        TreeMap<String, Block> byId = new TreeMap<>();
        for (int i = 0; i < expected.length; i++) {
            if (expected[i].counted()) {
                Block block = view.placed(i).getBlock();
                byId.put(BuiltInRegistries.BLOCK.getKey(block).toString(), block);
            }
        }
        Map<Item, Integer> itemIndex = new IdentityHashMap<>();
        Map<Block, Integer> plannedIndex = new IdentityHashMap<>();
        Set<String> holdable = new LinkedHashSet<>();
        Map<Block, Set<Block>> twoItemHighlights = new IdentityHashMap<>();
        byId.forEach((id, block) -> {
            TwoItemBlocks.Parts parts = TwoItemBlocks.of(block);
            if (parts == null) {
                plan(block, keys, plannedIndex, itemIndex, holdable);
                return;
            }
            plan(parts.base(), keys, plannedIndex, itemIndex, holdable);
            if (!plannedIndex.containsKey(parts.content())) {
                int item = index(parts.contentItem(), itemIndex, items);
                addPlanned(parts.content(), item, new int[] {item}, null, plannedIndex);
            }
            twoItemHighlights.computeIfAbsent(parts.base(), ignored -> new LinkedHashSet<>()).add(block);
            twoItemHighlights.computeIfAbsent(parts.content(), ignored -> new LinkedHashSet<>()).add(block);
        });
        for (String held : holdable) {
            Block heldBlock = CellKeys.known(held);
            if (heldBlock != null) {
                Set<Block> lit = Collections.newSetFromMap(new IdentityHashMap<>());
                BlockEquivalents.highlights(held, byId.keySet()).forEach(id -> lit.add(byId.get(id)));
                highlights.put(heldBlock, lit);
            }
        }
        twoItemHighlights.forEach((held, twoItem) -> highlights
                .computeIfAbsent(held, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).addAll(twoItem));
        return plannedIndex;
    }

    /** Numbers a planned block that an item places, once; a block no item places is left out. */
    private void plan(Block block, CellKeys keys, Map<Block, Integer> plannedIndex, Map<Item, Integer> itemIndex, Set<String> holdable) {
        if (plannedIndex.containsKey(block)) {
            return;
        }
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        BlockEquivalents.Equivalence equivalence = keys.equivalence(block);
        List<String> accepted = equivalence.accepted(id);
        holdable.addAll(accepted);
        Block placedWith = CellKeys.known(equivalence.placedWith());
        Item item = placedWith == null ? Items.AIR : placedWith.asItem();
        if (item == Items.AIR) {
            return;
        }
        int[] accepts = accepted.stream().map(CellKeys::known).filter(known -> known != null && known.asItem() != Items.AIR)
                .mapToInt(known -> index(known.asItem(), itemIndex, items)).distinct().toArray();
        addPlanned(block, index(item, itemIndex, items), accepts, equivalence.finish(), plannedIndex);
    }

    private void addPlanned(Block block, int placingItem, int[] accepted, BlockEquivalents.@Nullable Finish finish, Map<Block, Integer> plannedIndex) {
        plannedIndex.put(block, planned.size());
        planned.add(block);
        placingItems.add(placingItem);
        accepts.add(accepted);
        finishes.add(finish);
    }

    /** What building one position that holds {@code placed} takes; {@code plannedBlock} is -1 when no item places it. */
    private Demand demand(BlockState placed, int plannedBlock, Map<Block, Integer> blockIndex) {
        if (placed.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && placed.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                || placed.hasProperty(BlockStateProperties.BED_PART) && placed.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return Demand.NONE;
        }
        if (plannedBlock < 0) {
            return Demand.cantCarry(index(placed.getBlock(), blockIndex, blocks), 1);
        }
        return Demand.planned(plannedBlock, CellKeys.amount(placed));
    }

    private static <T> int index(T key, Map<T, Integer> indices, List<T> keys) {
        return indices.computeIfAbsent(key, added -> {
            keys.add(added);
            return keys.size() - 1;
        });
    }

    /** True while this comparison still describes the placement as it is. */
    boolean matches(Placement current) {
        return current.locked() && current.source().equals(placement.source()) && current.rotation() == placement.rotation()
                && current.mirror() == placement.mirror() && current.swaps().equals(placement.swaps())
                && PlacementTransform.bounds(current).equals(bounds);
    }

    public ComparisonState state() {
        return state;
    }

    /**
     * What the positions that still need their item ask for and the positions that need finishing, by
     * {@link #plannedBlock} and {@link #cantCarryBlock} index.
     */
    public MaterialCounts materials() {
        return materials;
    }

    /** The planned blocks an item places, in block ID order. */
    public List<Block> plannedBlocks() {
        return planned;
    }

    public Block plannedBlock(int index) {
        return planned.get(index);
    }

    /** The {@link #item} index of the item that places a planned block. */
    public int placingItem(int planned) {
        return placingItems.get(planned);
    }

    /** The {@link #item} indices of the items that count toward a planned block, in the order it takes them. */
    public int[] accepts(int planned) {
        return accepts.get(planned);
    }

    /** The step that finishes a planned block, or null when nothing does. */
    public BlockEquivalents.@Nullable Finish finish(int planned) {
        return finishes.get(planned);
    }

    /** The items that place or count toward the planned blocks. */
    public List<Item> items() {
        return items;
    }

    public Item item(int index) {
        return items.get(index);
    }

    /** The blocks of this placement that a held block places or counts toward. */
    public Set<Block> highlights(Block held) {
        return highlights.getOrDefault(held, Set.of(held));
    }

    public Block cantCarryBlock(int index) {
        return blocks.get(index);
    }

    /** The placement's box in the world, as compared. */
    public Bounds bounds() {
        return bounds;
    }

    /**
     * Sets a position's state and the amount found there, moving its demand and finishing in the material counts when
     * they change.
     */
    private void set(int x, int y, int z, int entry, CellState next, int found) {
        int index = state.index(x, y, z);
        int oldFound = state.found(index);
        CellState old = state.set(index, next, found);
        materials.move(y, demands[entry], old, oldFound, next, found);
        if (old != next && plannedOf[entry] >= 0) {
            if (old == CellState.NEEDS_FINISHING) {
                materials.removeFinishing(y, plannedOf[entry]);
            } else if (next == CellState.NEEDS_FINISHING) {
                materials.addFinishing(y, plannedOf[entry]);
            }
        }
    }

    /** The state at a world position, or null outside the placement. */
    public @Nullable CellState stateAt(BlockPos pos) {
        int x = pos.getX() - bounds.minX();
        int y = pos.getY() - bounds.minY();
        int z = pos.getZ() - bounds.minZ();
        return contains(x, y, z) ? stateAt(x, y, z) : null;
    }

    /** The schematic block as placed at a world position inside the placement. */
    public BlockState expectedAt(BlockPos pos) {
        return plannedAt(pos.getX() - bounds.minX(), pos.getY() - bounds.minY(), pos.getZ() - bounds.minZ());
    }

    /** The step that finishes the block expected at a world position inside the placement, or null when nothing does. */
    public BlockEquivalents.@Nullable Finish finishAt(BlockPos pos) {
        return finishAt(pos.getX() - bounds.minX(), pos.getY() - bounds.minY(), pos.getZ() - bounds.minZ());
    }

    /** True when a position relative to the placement's minimum corner lies inside it. */
    public boolean contains(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < state.sizeX() && y < state.sizeY() && z < state.sizeZ();
    }

    /** The state at a local position inside the placement. */
    public CellState stateAt(int x, int y, int z) {
        return state.state(state.index(x, y, z));
    }

    /** Block ID to the positions in layers {@code from} to {@code to} that show it while the game does not know it. */
    Map<String, Integer> unknownBlocks(int from, int to) {
        return unknown.sum(from, to);
    }

    /** The block ID as shown at a local position inside the placement when the game does not know it, otherwise null. */
    public @Nullable String unknownIdAt(int x, int y, int z) {
        int entry = view.paletteIndex(x, y, z, scratch);
        return view.unknown(entry) ? BlockStates.id(view.entry(entry)) : null;
    }

    /** What kind of position a local position inside the placement is: ignored, air, a block or one that can't be checked. */
    public Expected.Kind kindAt(int x, int y, int z) {
        return expected[view.paletteIndex(x, y, z, scratch)].kind();
    }

    /** The schematic block as placed, after swaps, rotation and mirror, at a local position inside the placement. */
    public BlockState plannedAt(int x, int y, int z) {
        return view.placed(view.paletteIndex(x, y, z, scratch));
    }

    /** The step that finishes the block at a local position inside the placement, or null when nothing does. */
    public BlockEquivalents.@Nullable Finish finishAt(int x, int y, int z) {
        int finish = expected[view.paletteIndex(x, y, z, scratch)].finish();
        return finish < 0 ? null : BlockEquivalents.Finish.values()[finish];
    }

    /**
     * The {@link #plannedBlock} index of what a local position inside the placement takes next: the content of a
     * two-item block whose base stands, otherwise the planned block or a two-item block's base; -1 when no item places it.
     */
    public int plannedIndexAt(int x, int y, int z) {
        int entry = view.paletteIndex(x, y, z, scratch);
        return contentOf[entry] >= 0 && stateAt(x, y, z) == CellState.INCOMPLETE ? contentOf[entry] : plannedOf[entry];
    }

    /** The candles, pickles or snow layers of a state, 2 for a double slab, otherwise 1. */
    public static int amount(BlockState state) {
        return CellKeys.amount(state);
    }

    /** The orientation properties, slab type and waterlogging of a state, as {@code name=value} pairs, for showing to the player. */
    public static String details(BlockState state) {
        StringBuilder text = new StringBuilder();
        for (Property<?> property : CellKeys.SHOWN) {
            if (state.hasProperty(property)) {
                text.append(text.isEmpty() ? "" : ", ").append(property.getName()).append('=')
                        .append(state.getValue(property).toString().toLowerCase(Locale.ROOT));
            }
        }
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            text.append(text.isEmpty() ? "" : ", ").append("waterlogged");
        }
        return text.toString();
    }

    /** How the comparison would judge a block at a local position inside the placement, without changing anything. */
    public CellState judge(int x, int y, int z, BlockState block) {
        return CellState.classify(expected[view.paletteIndex(x, y, z, scratch)], keys.found(block));
    }

    /** Compares one local position with the block the world holds there. */
    void compare(int x, int y, int z, BlockState world, CellKeys keys) {
        int entry = view.paletteIndex(x, y, z, scratch);
        CellState.Found found = keys.found(world);
        CellState next = CellState.classify(expected[entry], found);
        set(x, y, z, entry, next, found.amount());
        int index = state.index(x, y, z);
        if (next.boxed()) {
            int id = Block.getId(world);
            if (boxedBlocks.put(index, id) != id) {
                state.markBoxes(index);
            }
        } else {
            boxedBlocks.remove(index);
        }
    }

    /** The outline of the world block at a local position, or null when it fills its cell or has none. */
    private @Nullable List<AABB> foundOutline(int x, int y, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        BlockPos pos = new BlockPos(bounds.minX() + x, bounds.minY() + y, bounds.minZ() + z);
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        return shape.isEmpty() || Block.isShapeFullBlock(shape) ? null : shape.toAabbs();
    }

    /**
     * Compares every position of a section that lies in a loaded chunk; positions in unloaded chunks become unknown.
     * Returns the number of positions looked at.
     */
    int compareSection(int section, ChunkReader chunks, CellKeys keys) {
        int[] min = state.sectionMin(section);
        int maxX = Math.min(min[0] + ComparisonState.SECTION, state.sizeX());
        int maxY = Math.min(min[1] + ComparisonState.SECTION, state.sizeY());
        int maxZ = Math.min(min[2] + ComparisonState.SECTION, state.sizeZ());
        boolean loaded = true;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = min[2]; z < maxZ; z++) {
            for (int x = min[0]; x < maxX; x++) {
                LevelChunk chunk = chunks.chunk(bounds.minX() + x, bounds.minZ() + z);
                for (int y = min[1]; y < maxY; y++) {
                    if (chunk == null) {
                        int entry = view.paletteIndex(x, y, z, scratch);
                        set(x, y, z, entry, switch (expected[entry].kind()) {
                            case IGNORED -> CellState.IGNORED;
                            case CANT_CHECK -> CellState.CANT_CHECK;
                            default -> CellState.UNKNOWN;
                        }, 0);
                    } else {
                        compare(x, y, z, chunk.getBlockState(pos.set(bounds.minX() + x, bounds.minY() + y, bounds.minZ() + z)), keys);
                    }
                }
                loaded &= chunk != null;
            }
        }
        state.compared(section, loaded);
        return (maxX - min[0]) * (maxY - min[1]) * (maxZ - min[2]);
    }

    /** The ghost mask on the GPU, with every change since the last call uploaded. Render thread only. */
    GpuBuffer mask() {
        RenderSystem.assertOnRenderThread();
        byte[] bits = state.mask();
        if (mask == null) {
            state.takeDirtyMask();
            ByteBuffer data = direct(bits, 0, bits.length);
            try {
                mask = RenderSystem.getDevice().createBuffer(() -> "Heavymatica ghost mask", GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, data);
            } finally {
                MemoryUtil.memFree(data);
            }
            return mask;
        }
        int[] dirty = state.takeDirtyMask();
        if (dirty != null) {
            ByteBuffer data = direct(bits, dirty[0], dirty[1] - dirty[0]);
            try {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(mask.slice(dirty[0], dirty[1] - dirty[0]), data);
            } finally {
                MemoryUtil.memFree(data);
            }
        }
        return mask;
    }

    MistakeBoxes boxes() {
        return boxes;
    }

    private static ByteBuffer direct(byte[] bytes, int from, int length) {
        ByteBuffer buffer = MemoryUtil.memAlloc(length);
        buffer.put(bytes, from, length).flip();
        return buffer;
    }

    @Override
    public void close() {
        if (mask != null) {
            mask.close();
            mask = null;
        }
        boxes.close();
    }

    /** The chunk holding a world column, or null while it is not loaded. */
    interface ChunkReader {

        @Nullable LevelChunk chunk(int blockX, int blockZ);
    }
}

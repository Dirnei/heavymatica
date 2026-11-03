package io.github.dirnei.mc.heavymatica.paste;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.formats.Nbt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.Nullable;

/**
 * A paste or an undo, run on the integrated server's thread a few sections per tick. Progress, the outcome and
 * cancelling are safe to use from the client thread.
 */
final class PasteJob {

    /**
     * Clients see the change; no neighbour or shape updates, no drops, no container spill and no {@code onPlace}, so
     * nothing reacts until something else updates the block. The same flags as {@code /setblock ... strict}.
     */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

    enum Kind {
        PASTE,
        UNDO,
    }

    enum Outcome {
        RUNNING,
        DONE,
        /** The player was not in creative mode, or the dimension is gone, when the job started. */
        REFUSED,
        CANCELLED,
    }

    private static final PasteMode.States<BlockState> STATES = new PasteMode.States<>() {
        @Override
        public boolean air(BlockState state) {
            return state.isAir();
        }

        @Override
        public boolean replaceable(BlockState state) {
            return state.canBeReplaced();
        }
    };

    private final Kind kind;
    private final MinecraftServer server;
    private final UUID player;
    private final @Nullable PastePlan<BlockState> plan;
    private final @Nullable PasteMode mode;
    private final PasteRecord record;
    private final long total;
    private final AtomicLong done = new AtomicLong();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private volatile Outcome outcome = Outcome.RUNNING;
    private volatile boolean cancelled;
    private @Nullable ServerLevel level;
    private int next;

    private PasteJob(Kind kind, MinecraftServer server, UUID player, @Nullable PastePlan<BlockState> plan, @Nullable PasteMode mode, PasteRecord record,
                     long total) {
        this.kind = kind;
        this.server = server;
        this.player = player;
        this.plan = plan;
        this.mode = mode;
        this.record = record;
        this.total = total;
    }

    /** Pastes the plan and records what it changes into {@code record}, which must be empty. */
    static PasteJob paste(MinecraftServer server, UUID player, PastePlan<BlockState> plan, PasteMode mode, PasteRecord record) {
        return new PasteJob(Kind.PASTE, server, player, plan, mode, record, plan.total());
    }

    /** Restores what a paste recorded, last change first. */
    static PasteJob undo(MinecraftServer server, UUID player, PasteRecord record) {
        return new PasteJob(Kind.UNDO, server, player, null, null, record, record.size());
    }

    Kind kind() {
        return kind;
    }

    MinecraftServer server() {
        return server;
    }

    PasteRecord record() {
        return record;
    }

    Outcome outcome() {
        return outcome;
    }

    /** The share of positions handled, from 0 to 1. */
    double progress() {
        return total == 0 ? 1 : (double) done.get() / total;
    }

    /** Stops the job before its next section. */
    void cancel() {
        cancelled = true;
    }

    /** Runs whole sections until {@code budgetNanos} are used, at least one. Server thread only. */
    void tick(long budgetNanos) {
        if (outcome != Outcome.RUNNING) {
            return;
        }
        if (cancelled) {
            outcome = Outcome.CANCELLED;
            return;
        }
        if (level == null && !start()) {
            outcome = Outcome.REFUSED;
            return;
        }
        long deadline = System.nanoTime() + budgetNanos;
        do {
            if (next >= sections()) {
                outcome = Outcome.DONE;
                return;
            }
            if (kind == Kind.PASTE) {
                paste(plan.sections().get(next++));
            } else {
                undo(record.batches() - 1 - next++);
            }
        } while (System.nanoTime() < deadline && !cancelled);
        if (next >= sections()) {
            outcome = Outcome.DONE;
        }
    }

    private int sections() {
        return kind == Kind.PASTE ? plan.sections().size() : record.batches();
    }

    private boolean start() {
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
        if (serverPlayer == null || !serverPlayer.isCreative()) {
            return false;
        }
        level = server.getLevel(record.dimension());
        return level != null;
    }

    private void paste(PastePlan.Section<BlockState> section) {
        ServerLevel level = this.level;
        if (section.size() > 0 && !level.isOutsideBuildHeight(section.y(0))) {
            level.getChunk(section.x(0) >> 4, section.z(0) >> 4);
        }
        for (int i = 0; i < section.size(); i++) {
            cursor.set(section.x(i), section.y(i), section.z(i));
            if (level.isOutsideBuildHeight(cursor)) {
                continue;
            }
            BlockState planned = section.state(i);
            Nbt.Compound blockEntity = section.blockEntity(i);
            BlockState found = level.getBlockState(cursor);
            if (!mode.changes(planned, blockEntity != null, found, STATES)) {
                continue;
            }
            BlockEntity old = level.getBlockEntity(cursor);
            record.add(cursor, found, old == null ? null : old.saveWithFullMetadata(level.registryAccess()));
            if (old instanceof Clearable clearable) {
                clearable.clearContent();
            }
            level.setBlock(cursor, planned, FLAGS);
            if (blockEntity != null) {
                load(level, cursor, convert(blockEntity));
            }
        }
        record.endBatch();
        done.addAndGet(section.size());
    }

    private void undo(int batch) {
        ServerLevel level = this.level;
        int start = record.batchStart(batch);
        int end = record.batchEnd(batch);
        cursor.set(record.position(start));
        level.getChunk(cursor.getX() >> 4, cursor.getZ() >> 4);
        for (int i = end - 1; i >= start; i--) {
            cursor.set(record.position(i));
            if (level.getBlockEntity(cursor) instanceof Clearable clearable) {
                clearable.clearContent();
            }
            level.setBlock(cursor, record.state(i), FLAGS);
            CompoundTag blockEntity = record.blockEntity(i);
            if (blockEntity != null) {
                load(level, cursor, blockEntity);
            }
        }
        done.addAndGet(end - start);
    }

    /** Loads block-entity data into the block entity at the position; data the game can't read is skipped. */
    private static void load(ServerLevel level, BlockPos pos, @Nullable CompoundTag tag) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || tag == null) {
            return;
        }
        ProblemReporter.Collector problems = new ProblemReporter.Collector(blockEntity.problemPath());
        try {
            blockEntity.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), tag));
            blockEntity.setChanged();
        } catch (RuntimeException e) {
            Heavymatica.LOGGER.debug("Skipped block entity data at {} that the game can't read", pos.toShortString(), e);
        }
        if (!problems.isEmpty()) {
            Heavymatica.LOGGER.debug("Block entity data at {} partly unreadable: {}", pos.toShortString(), problems.getTreeReport());
        }
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private static @Nullable CompoundTag convert(Nbt.Compound tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Nbt.writeUncompressed(tag, bytes);
            return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        } catch (IOException | RuntimeException e) {
            Heavymatica.LOGGER.debug("Skipped block entity data that can't be converted", e);
            return null;
        }
    }
}

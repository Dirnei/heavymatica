package io.github.dirnei.mc.heavymatica.capture;

import io.github.dirnei.mc.heavymatica.formats.BlockStates;
import io.github.dirnei.mc.heavymatica.formats.Nbt;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import io.github.dirnei.mc.heavymatica.formats.SchematicReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The client's world for a capture. Block states come from the client level. Block entities come from the integrated
 * server's level in single player, since the client only holds what the server sends for rendering; on a remote
 * server they come from the client level.
 */
final class ClientCaptureWorld implements CaptureSnapshot.World {

    private final ClientLevel level;
    private final @Nullable IntegratedServer server;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private @Nullable LevelChunk chunk;
    private long chunkKey = Long.MIN_VALUE;

    ClientCaptureWorld(ClientLevel level, @Nullable IntegratedServer server) {
        this.level = level;
        this.server = server;
    }

    @Override
    public boolean loaded(int chunkX, int chunkZ) {
        chunk = null;
        // ClientLevel.hasChunk is always true; the chunk cache tells whether a chunk really is loaded.
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }

    @Override
    public Object state(int x, int y, int z) {
        return chunk(x >> 4, z >> 4).getBlockState(cursor.set(x, y, z));
    }

    @Override
    public String name(Object state) {
        return BlockStates.canonical(BlockStateParser.serialize((BlockState) state));
    }

    @Override
    public CompletableFuture<Map<SchematicModel.Pos, Nbt.Compound>> blockEntities(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : chunk(minX >> 4, minZ >> 4).getBlockEntities().keySet()) {
            if (pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                positions.add(pos.immutable());
            }
        }
        if (positions.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        ServerLevel serverLevel = server == null ? null : server.getLevel(level.dimension());
        if (serverLevel == null) {
            return CompletableFuture.completedFuture(save(level, positions));
        }
        return server.submit(() -> save(serverLevel, positions));
    }

    private static Map<SchematicModel.Pos, Nbt.Compound> save(Level from, List<BlockPos> positions) {
        HolderLookup.Provider registries = from.registryAccess();
        Map<SchematicModel.Pos, Nbt.Compound> saved = new HashMap<>();
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = from.getBlockEntity(pos);
            if (blockEntity != null) {
                Nbt.Compound tag = convert(blockEntity.saveWithFullMetadata(registries));
                String id = tag.string("id");
                saved.put(new SchematicModel.Pos(pos.getX(), pos.getY(), pos.getZ()), SchematicReader.blockEntity(id == null ? "" : id, tag.entries()));
            }
        }
        return saved;
    }

    private static Nbt.Compound convert(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(bytes));
            return Nbt.readRoot(new ByteArrayInputStream(bytes.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private LevelChunk chunk(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
        if (chunk == null || key != chunkKey) {
            chunk = level.getChunk(chunkX, chunkZ);
            chunkKey = key;
        }
        return chunk;
    }
}

package dev.drimoz.contiguousstorage.network;

import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Which positions are physically connected to a Storage Controller: a breadth-first walk over the
 * six faces, from the controller, through drawers and conductor blocks (CLAUDE.md §5).
 *
 * <p>It never loads a chunk. Chunks come from a {@code LongFunction} keyed by
 * {@link ChunkPos#asLong}, which in game is {@link #loadedChunks}: {@code getChunkNow}, null when
 * the chunk is not ready. A null anywhere on the way aborts the walk: a chain that cannot be read is
 * not a broken chain, and the caller must then remove nothing.
 */
public final class NetworkWalker {

    /** Blocks that carry a network without being linked to it. Empty by default; filled by packs. */
    public static final TagKey<Block> CONDUCTORS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ContiguousStorage.MOD_ID, "conductors"));

    private NetworkWalker() {
    }

    /**
     * Every position reachable from {@code controllerPos} inside {@code range}, or null if a chunk
     * the walk needed was not loaded. The controller itself is not in the result.
     */
    public static @Nullable Set<BlockPos> walk(BlockPos controllerPos, AABB range, LongFunction<LevelChunk> chunks) {
        Set<BlockPos> reached = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(controllerPos);
        while (!queue.isEmpty()) {
            BlockPos from = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos pos = from.relative(side);
                if (pos.equals(controllerPos) || reached.contains(pos) || !range.contains(Vec3.atCenterOf(pos))) {
                    continue;
                }
                LevelChunk chunk = chunks.apply(ChunkPos.asLong(pos));
                if (chunk == null) {
                    return null;
                }
                if (conducts(chunk, pos)) {
                    reached.add(pos);
                    queue.add(pos);
                }
            }
        }
        return reached;
    }

    /**
     * The same area Functional Storage accepts in {@code addConnectedDrawers} and keeps in
     * {@code ConnectedDrawers.rebuild} - read from the 1.5.7 bytecode.
     */
    public static AABB range(StorageControllerTile<?> controller) {
        return new AABB(controller.getBlockPos()).inflate(controller.getStorageMultiplier());
    }

    /** Chunk access for the server thread that never blocks: null for a chunk that is not ready. */
    public static LongFunction<LevelChunk> loadedChunks(ServerLevel level) {
        return key -> level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
    }

    /**
     * Any linkable drawer except another controller (first one wins, DESIGN.md §4), or a block in
     * the conductor tag. The block entity is read from the chunk's map: a plain lookup, where
     * {@code getBlockEntity} could create one.
     */
    private static boolean conducts(LevelChunk chunk, BlockPos pos) {
        BlockEntity be = chunk.getBlockEntities().get(pos);
        if (be instanceof ControllableDrawerTile<?>) {
            return !(be instanceof StorageControllerTile<?>);
        }
        return chunk.getBlockState(pos).is(CONDUCTORS);
    }
}

package dev.drimoz.contiguousstorage.network;

import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import dev.drimoz.contiguousstorage.block.TrimBlock;
import net.minecraft.world.level.block.state.BlockState;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

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

    /** Blocks that carry a network without being linked to it. Ships Storage Drawers' trims; packs add to it. */
    public static final TagKey<Block> CONDUCTORS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(ContiguousStorage.MOD_ID, "conductors"));

    static final Direction[] DIRECTIONS = Direction.values();

    private NetworkWalker() {
    }

    /**
     * Every position reachable from {@code controllerPos} inside {@code range}, or null if a chunk
     * the walk needed was not loaded. The controller itself is not in the result.
     */
    public static @Nullable Set<BlockPos> walk(BlockPos controllerPos, AABB range, LongFunction<LevelChunk> chunks) {
        Walk walk = walkTree(controllerPos, range, chunks);
        return walk == null ? null : walk.parent().keySet().longStream().mapToObj(BlockPos::of).collect(Collectors.toSet());
    }

    /**
     * What one walk found, positions as {@link BlockPos#asLong}.
     *
     * @param parent     every member and the member it was first reached from - the controller
     *                   itself for those touching it
     * @param linked     the members that are drawers linked to this controller
     * @param free       the members that are drawers linked to nothing
     * @param conductors the members that are conductor blocks
     */
    public record Walk(Long2LongOpenHashMap parent, LongOpenHashSet linked, LongOpenHashSet free, LongOpenHashSet conductors) {
    }

    /** The same walk, as a tree, with what each member is. Null if a chunk the walk needed was not loaded. */
    public static @Nullable Walk walkTree(BlockPos controllerPos, AABB range, LongFunction<LevelChunk> chunks) {
        long root = controllerPos.asLong();
        Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        LongOpenHashSet linked = new LongOpenHashSet();
        LongOpenHashSet free = new LongOpenHashSet();
        LongOpenHashSet conductors = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        queue.enqueue(root);
        while (!queue.isEmpty()) {
            long from = queue.dequeueLong();
            for (Direction side : DIRECTIONS) {
                long pos = BlockPos.offset(from, side);
                if (pos == root || parent.containsKey(pos)) {
                    continue;
                }
                cursor.set(pos);
                if (!range.contains(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5)) {
                    continue;
                }
                LevelChunk chunk = chunks.apply(ChunkPos.pack(cursor));
                if (chunk == null) {
                    return null;
                }
                BlockEntity be = chunk.getBlockEntities().get(cursor);
                if (be instanceof ControllableDrawerTile<?> drawer) {
                    if (be instanceof StorageControllerTile<?>) {
                        continue;
                    }
                    BlockPos owner = drawer.getControllerPos();
                    if (owner == null) {
                        free.add(pos);
                    } else if (owner.equals(controllerPos)) {
                        linked.add(pos);
                    } else {
                        continue;
                    }
                } else if (isConductor(chunk.getBlockState(cursor))) {
                    conductors.add(pos);
                } else {
                    continue;
                }
                parent.put(pos, from);
                queue.enqueue(pos);
            }
        }
        return new Walk(parent, linked, free, conductors);
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
     * Whether the block at {@code pos} carries {@code controllerPos}'s network: a drawer that is free
     * or already its own, or a block in the conductor tag. Another controller, and a drawer linked to
     * another controller, do not: first one wins (DESIGN.md §4), and without that a second controller
     * would reach through the first one's drawers and take the free drawers placed against them. The
     * block entity is read from the chunk's map: a plain lookup, where {@code getBlockEntity} could
     * create one.
     */
    static boolean isMember(LevelChunk chunk, BlockPos pos, BlockPos controllerPos) {
        BlockEntity be = chunk.getBlockEntities().get(pos);
        if (be instanceof ControllableDrawerTile<?> drawer) {
            BlockPos owner = drawer.getControllerPos();
            return !(be instanceof StorageControllerTile<?>) && (owner == null || owner.equals(controllerPos));
        }
        return isConductor(chunk.getBlockState(pos));
    }

    /**
     * A block that carries a network without being linked to it: one in the conductor tag, or one of
     * this mod's trims while they are enabled - turned off, a placed trim is only decoration.
     */
    public static boolean isConductor(BlockState state) {
        return state.is(CONDUCTORS) || (state.getBlock() instanceof TrimBlock && CSConfig.TRIMS_ENABLED.get());
    }
}

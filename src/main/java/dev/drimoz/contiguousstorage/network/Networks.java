package dev.drimoz.contiguousstorage.network;

import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.LinkingToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Keeps a controller's links in line with what physically touches it (CLAUDE.md §5).
 */
public final class Networks {

    /** {@link #validate} or {@link #linkFree} could not read the whole network and changed nothing. */
    public static final int ABORTED = -1;

    /**
     * Positions where something was placed this tick, per dimension. Linking waits for the tick:
     * {@code EntityPlaceEvent} fires while block snapshots are captured, and another mod may still
     * cancel the placement (CLAUDE.md §5).
     */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PLACED = new HashMap<>();

    /** Controllers to validate at the end of this tick, per dimension. STRICT only. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Player>> DIRTY = new HashMap<>();

    /**
     * Controllers in loaded chunks, per dimension, for the safety net - FS keeps no such list, and a
     * controller's tile cannot be ticked from here. Positions, not tiles: a tile can be replaced.
     */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> KNOWN = new HashMap<>();

    /** How many times {@link #validate} has walked a network. Read by game test 11; nothing else. */
    public static long validations;

    private Networks() {
    }

    public static void chunkLoaded(ServerLevel level, LevelChunk chunk) {
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof StorageControllerTile<?>) {
                known(level).add(be.getBlockPos().immutable());
            }
        }
    }

    public static void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        Set<BlockPos> known = KNOWN.get(level.dimension());
        if (known != null) {
            ChunkPos unloaded = chunk.getPos();
            known.removeIf(pos -> new ChunkPos(pos).equals(unloaded));
        }
    }

    /**
     * The safety net: every {@code interval} ticks, every known controller is validated, whatever
     * the events missed. A controller whose chunk is not ready is skipped, not forgotten; one that is
     * gone is forgotten.
     */
    public static void safetyNet(ServerLevel level, int interval) {
        Set<BlockPos> known = KNOWN.get(level.dimension());
        if (interval <= 0 || known == null || level.getGameTime() % interval != 0) {
            return;
        }
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        for (Iterator<BlockPos> it = known.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            LevelChunk chunk = chunks.apply(ChunkPos.asLong(pos));
            if (chunk == null) {
                continue;
            }
            if (chunk.getBlockEntities().get(pos) instanceof StorageControllerTile<?> controller) {
                validate(controller, chunks);
            } else {
                it.remove();
            }
        }
    }

    private static Set<BlockPos> known(ServerLevel level) {
        return KNOWN.computeIfAbsent(level.dimension(), key -> new HashSet<>());
    }

    /**
     * A block changed at {@code pos}: the controller of every neighbour that is a controller or a
     * linked drawer must be checked again. The hot path of the mod - six map lookups in chunks that
     * are already loaded, nothing else (CLAUDE.md §5).
     */
    public static void neighborChanged(ServerLevel level, BlockPos pos) {
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        for (Direction side : Direction.values()) {
            BlockEntity be = blockEntity(pos.relative(side), chunks);
            BlockPos controller = be instanceof StorageControllerTile<?> ? be.getBlockPos()
                    : be instanceof ControllableDrawerTile<?> drawer ? drawer.getControllerPos()
                    : null;
            if (controller != null) {
                markDirty(level, controller);
            }
        }
    }

    public static void markDirty(ServerLevel level, BlockPos controller) {
        DIRTY.computeIfAbsent(level.dimension(), key -> new HashMap<>()).putIfAbsent(controller.immutable(), null);
    }

    /** Same, and {@code player} is told how many drawers the validation unlinks - the Linking Tool. */
    public static void markDirty(ServerLevel level, BlockPos controller, Player player) {
        DIRTY.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(controller.immutable(), player);
    }

    /** Validates every controller marked since the last tick, if it is still there and loaded. */
    public static void validateDirty(ServerLevel level) {
        Map<BlockPos, Player> dirty = DIRTY.remove(level.dimension());
        if (dirty == null) {
            return;
        }
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        dirty.forEach((pos, player) -> {
            if (blockEntity(pos, chunks) instanceof StorageControllerTile<?> controller) {
                // Also how a controller set by command or by another mod joins the safety net.
                known(level).add(pos);
                int unlinked = validate(controller, chunks);
                if (unlinked > 0 && player != null) {
                    // With a fallback: the mod is server-side only, a client without it has no lang file.
                    player.displayClientMessage(Component.translatableWithFallback(
                            "message.contiguousstorage.not_connected",
                            "%s drawer(s) not touching the controller's network were not linked", unlinked), true);
                }
            }
        });
    }

    public static void placed(ServerLevel level, BlockPos pos) {
        PLACED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    /**
     * Auto-link for everything placed since the last tick: each controller that the placed block is,
     * or touches directly or through one of its drawers, links the free drawers it now reaches.
     */
    public static void linkPlaced(ServerLevel level) {
        Set<BlockPos> placed = PLACED.remove(level.dimension());
        if (placed == null) {
            return;
        }
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        Set<StorageControllerTile<?>> controllers = new LinkedHashSet<>();
        for (BlockPos pos : placed) {
            if (blockEntity(pos, chunks) instanceof StorageControllerTile<?>) {
                known(level).add(pos);
            }
            addOwners(pos, chunks, controllers);
        }
        controllers.forEach(controller -> linkFree(controller, chunks));
    }

    /**
     * Links every free drawer this controller reaches, in one {@code addConnectedDrawers(ADD, …)}.
     * A drawer that already has a controller keeps it: first one wins (DESIGN.md §4), and moving it
     * would make FS look up its old controller with a blocking read (CLAUDE.md §3).
     *
     * @return how many drawers were linked, or {@link #ABORTED} if the walk met an unloaded chunk
     */
    public static int linkFree(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        Set<BlockPos> connected = NetworkWalker.walk(controller.getBlockPos(), NetworkWalker.range(controller), chunks);
        if (connected == null) {
            return ABORTED;
        }
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos pos : connected) {
            if (blockEntity(pos, chunks) instanceof ControllableDrawerTile<?> drawer && drawer.getControllerPos() == null) {
                free.add(pos);
            }
        }
        if (!free.isEmpty()) {
            controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, free.toArray(BlockPos[]::new));
        }
        return free.size();
    }

    /** How many conductor blocks {@link #addOwners} goes through before giving up. */
    private static final int CONDUCTOR_SEARCH_LIMIT = 512;

    /**
     * The owners of {@code start} and of its neighbours - and, since a conductor carries no
     * {@code controllerPos}, of everything along the conductor blocks it touches: a drawer placed at
     * the end of a run of conductors finds the network at the other end.
     */
    private static void addOwners(BlockPos start, LongFunction<LevelChunk> chunks, Set<StorageControllerTile<?>> out) {
        // ponytail: capped flood; a longer run of conductors waits for the safety net (STRICT) or
        // for a drawer placed closer to the network (ASSIST).
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        addOwner(start, chunks, out);
        while (!queue.isEmpty() && seen.size() <= CONDUCTOR_SEARCH_LIMIT) {
            BlockPos from = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos pos = from.relative(side);
                if (seen.add(pos)) {
                    addOwner(pos, chunks, out);
                    if (isConductor(pos, chunks)) {
                        queue.add(pos);
                    }
                }
            }
        }
    }

    private static boolean isConductor(BlockPos pos, LongFunction<LevelChunk> chunks) {
        LevelChunk chunk = chunks.apply(ChunkPos.asLong(pos));
        return chunk != null && chunk.getBlockState(pos).is(NetworkWalker.CONDUCTORS);
    }

    /** The controller at {@code pos}, or the one the drawer at {@code pos} is linked to, if loaded. */
    private static void addOwner(BlockPos pos, LongFunction<LevelChunk> chunks, Set<StorageControllerTile<?>> out) {
        BlockEntity be = blockEntity(pos, chunks);
        if (be instanceof StorageControllerTile<?> controller) {
            out.add(controller);
        } else if (be instanceof ControllableDrawerTile<?> drawer && drawer.getControllerPos() != null
                && blockEntity(drawer.getControllerPos(), chunks) instanceof StorageControllerTile<?> controller) {
            out.add(controller);
        }
    }

    private static @Nullable BlockEntity blockEntity(BlockPos pos, LongFunction<LevelChunk> chunks) {
        LevelChunk chunk = chunks.apply(ChunkPos.asLong(pos));
        return chunk == null ? null : chunk.getBlockEntities().get(pos);
    }

    public static int validate(ServerLevel level, StorageControllerTile<?> controller) {
        return validate(controller, NetworkWalker.loadedChunks(level));
    }

    /**
     * Unlinks every drawer that points at this controller but is no longer connected to it, in one
     * {@code addConnectedDrawers(REMOVE, …)} call, so FS rebuilds once.
     *
     * <p>Drawers are found by their {@code controllerPos}, not by the controller's list: FS drops
     * unloaded drawers from the list without unlinking them (CLAUDE.md §3). Only drawers in loaded
     * chunks are candidates - removing one in an unloaded chunk would make FS load it.
     *
     * @return how many drawers were unlinked, or {@link #ABORTED} if the walk met an unloaded chunk
     */
    public static int validate(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        BlockPos controllerPos = controller.getBlockPos();
        AABB range = NetworkWalker.range(controller);
        validations++;
        Set<BlockPos> connected = NetworkWalker.walk(controllerPos, range, chunks);
        if (connected == null) {
            return ABORTED;
        }

        List<BlockPos> stray = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(range.minX); cx <= SectionPos.blockToSectionCoord(range.maxX); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(range.minZ); cz <= SectionPos.blockToSectionCoord(range.maxZ); cz++) {
                LevelChunk chunk = chunks.apply(ChunkPos.asLong(cx, cz));
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ControllableDrawerTile<?> drawer
                            && !(be instanceof StorageControllerTile<?>)
                            && controllerPos.equals(drawer.getControllerPos())
                            && !connected.contains(be.getBlockPos())) {
                        stray.add(be.getBlockPos());
                    }
                }
            }
        }
        if (!stray.isEmpty()) {
            controller.addConnectedDrawers(LinkingToolItem.ActionMode.REMOVE, stray.toArray(BlockPos[]::new));
        }
        return stray.size();
    }
}

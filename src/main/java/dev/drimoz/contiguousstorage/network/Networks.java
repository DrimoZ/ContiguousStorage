package dev.drimoz.contiguousstorage.network;

import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.LinkingToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final Map<ResourceKey<Level>, Set<BlockPos>> DIRTY = new HashMap<>();

    private Networks() {
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
        DIRTY.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(controller.immutable());
    }

    /** Validates every controller marked since the last tick, if it is still there and loaded. */
    public static void validateDirty(ServerLevel level) {
        Set<BlockPos> dirty = DIRTY.remove(level.dimension());
        if (dirty == null) {
            return;
        }
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        for (BlockPos pos : dirty) {
            if (blockEntity(pos, chunks) instanceof StorageControllerTile<?> controller) {
                validate(controller, chunks);
            }
        }
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
            addOwner(pos, chunks, controllers);
            for (Direction side : Direction.values()) {
                addOwner(pos.relative(side), chunks, controllers);
            }
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

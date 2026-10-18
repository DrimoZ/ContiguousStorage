package dev.drimoz.contiguousstorage.network;

import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.LinkingToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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

    /** Drawers that get a puff of particles per unlink or auto-link, so a 2 000-drawer cut does not flood a client. */
    public static final int PARTICLE_CAP = 32;

    /**
     * Who is told what a validation unlinked, and in which words. With an English fallback: the mod
     * is server-side only, and a client without it has no lang file.
     */
    private record Report(Player player, String key, String fallback) {
        void send(int unlinked) {
            player.displayClientMessage(Component.translatableWithFallback(key, fallback, unlinked), true);
        }
    }

    /**
     * Positions where something was placed this tick, per dimension. Linking waits for the tick:
     * {@code EntityPlaceEvent} fires while block snapshots are captured, and another mod may still
     * cancel the placement (CLAUDE.md §5). The value is who placed it, null for anything but a player.
     */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Player>> PLACED = new HashMap<>();

    /** Controllers to validate at the end of this tick, per dimension, and who to report to. STRICT only. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Report>> DIRTY = new HashMap<>();

    /**
     * Controllers in loaded chunks, per dimension, for the safety net - FS keeps no such list, and a
     * controller's tile cannot be ticked from here. Positions, not tiles: a tile can be replaced.
     */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> KNOWN = new HashMap<>();

    /** How many times {@link #validate} has walked a network. Read by game test 11; nothing else. */
    public static long validations;

    /** How many drawers got particles. Read by game test 13; nothing else. */
    public static long particlesSent;

    private Networks() {
    }

    // --- Registry and safety net ---------------------------------------------------------------

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

    /**
     * Puts the controller at {@code pos}, if there is one, on the safety net. Called whatever the
     * mode for a placement, and in ASSIST for a block update: a controller that appeared while the
     * world was not STRICT must be checked from the moment it is (found on a dedicated server).
     */
    public static void register(ServerLevel level, BlockPos pos) {
        if (blockEntity(pos, NetworkWalker.loadedChunks(level)) instanceof StorageControllerTile<?>) {
            known(level).add(pos.immutable());
        }
    }

    private static Set<BlockPos> known(ServerLevel level) {
        return KNOWN.computeIfAbsent(level.dimension(), key -> new HashSet<>());
    }

    // --- Marking controllers to validate (STRICT) ----------------------------------------------

    /**
     * A block changed at {@code pos}: the controller of every neighbour that is a controller or a
     * linked drawer must be checked again. The hot path of the mod - six map lookups in chunks that
     * are already loaded, nothing else (CLAUDE.md §5).
     */
    public static void neighborChanged(ServerLevel level, BlockPos pos) {
        register(level, pos);
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        for (Direction side : Direction.values()) {
            BlockEntity be = blockEntity(pos.relative(side), chunks);
            BlockPos controller = be instanceof StorageControllerTile<?> ? be.getBlockPos()
                    : be instanceof ControllableDrawerTile<?> drawer ? drawer.getControllerPos()
                    : null;
            if (controller != null) {
                mark(level, controller, null);
            }
        }
    }

    /** The Linking Tool was used with this controller: tell the player what the next tick undoes. */
    public static void linkingToolUsed(ServerLevel level, BlockPos controller, Player player) {
        mark(level, controller, new Report(player, "message.contiguousstorage.not_connected",
                "%s drawer(s) not touching the controller's network were not linked"));
    }

    /**
     * A player is breaking the block at {@code pos} (before it goes): the controllers it belongs to
     * or touches are checked at the next tick, and the player is told how many drawers it cut off.
     */
    public static void broken(ServerLevel level, BlockPos pos, Player player) {
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        Set<StorageControllerTile<?>> owners = new LinkedHashSet<>();
        addOwner(pos, chunks, owners);
        for (Direction side : Direction.values()) {
            addOwner(pos.relative(side), chunks, owners);
        }
        Report report = new Report(player, "message.contiguousstorage.disconnected", "%s drawer(s) disconnected");
        owners.forEach(controller -> mark(level, controller.getBlockPos(), report));
    }

    /** A report replaces none; none never replaces a report. */
    private static void mark(ServerLevel level, BlockPos controller, @Nullable Report report) {
        Map<BlockPos, Report> dirty = DIRTY.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        if (report == null) {
            dirty.putIfAbsent(controller.immutable(), null);
        } else {
            dirty.put(controller.immutable(), report);
        }
    }

    /** Validates every controller marked since the last tick, if it is still there and loaded. */
    public static void validateDirty(ServerLevel level) {
        Map<BlockPos, Report> dirty = DIRTY.remove(level.dimension());
        if (dirty == null) {
            return;
        }
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        dirty.forEach((pos, report) -> {
            if (blockEntity(pos, chunks) instanceof StorageControllerTile<?> controller) {
                // Also how a controller set by command or by another mod joins the safety net.
                known(level).add(pos);
                List<BlockPos> unlinked = unlink(controller, chunks);
                if (unlinked != null && !unlinked.isEmpty() && report != null) {
                    report.send(unlinked.size());
                }
            }
        });
    }

    // --- Auto-link -----------------------------------------------------------------------------

    public static void placed(ServerLevel level, BlockPos pos, @Nullable Player player) {
        PLACED.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(pos.immutable(), player);
    }

    /**
     * Auto-link for everything placed since the last tick: each controller that the placed block is,
     * or touches directly or through one of its drawers, links the free drawers it now reaches.
     *
     * <p>Only for a controller the placer may interact with (IDEAS.md §4): without that, a drawer
     * set against someone else's wall would join their network and take what it stores - the
     * Linking Tool at least needs a click on the controller first. A placement by anything but a
     * player links nothing.
     */
    public static void linkPlaced(ServerLevel level) {
        Map<BlockPos, Player> placed = PLACED.remove(level.dimension());
        if (placed == null) {
            return;
        }
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        Set<StorageControllerTile<?>> controllers = new LinkedHashSet<>();
        placed.forEach((pos, player) -> {
            if (player == null) {
                return;
            }
            Set<StorageControllerTile<?>> owners = new LinkedHashSet<>();
            addOwners(pos, chunks, owners);
            owners.removeIf(controller -> !level.mayInteract(player, controller.getBlockPos()));
            controllers.addAll(owners);
        });
        controllers.forEach(controller -> link(controller, chunks));
    }

    /** @return how many drawers were linked, or {@link #ABORTED} if the walk met an unloaded chunk */
    public static int linkFree(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        List<BlockPos> linked = link(controller, chunks);
        return linked == null ? ABORTED : linked.size();
    }

    /**
     * Links every free drawer this controller reaches, in one {@code addConnectedDrawers(ADD, …)}.
     * A drawer that already has a controller keeps it: first one wins (DESIGN.md §4), and moving it
     * would make FS look up its old controller with a blocking read (CLAUDE.md §3).
     *
     * @return the drawers linked, or null if the walk met an unloaded chunk
     */
    private static @Nullable List<BlockPos> link(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        Set<BlockPos> connected = NetworkWalker.walk(controller.getBlockPos(), NetworkWalker.range(controller), chunks);
        if (connected == null) {
            return null;
        }
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos pos : connected) {
            if (blockEntity(pos, chunks) instanceof ControllableDrawerTile<?> drawer && drawer.getControllerPos() == null) {
                free.add(pos);
            }
        }
        if (!free.isEmpty()) {
            controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, free.toArray(BlockPos[]::new));
            particles(controller, free, ParticleTypes.HAPPY_VILLAGER);
        }
        return free;
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

    // --- Validation ----------------------------------------------------------------------------

    public static int validate(ServerLevel level, StorageControllerTile<?> controller) {
        return validate(controller, NetworkWalker.loadedChunks(level));
    }

    /** @return how many drawers were unlinked, or {@link #ABORTED} if the walk met an unloaded chunk */
    public static int validate(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        List<BlockPos> unlinked = unlink(controller, chunks);
        return unlinked == null ? ABORTED : unlinked.size();
    }

    /** Unlinks {@link #strays} in one {@code addConnectedDrawers(REMOVE, …)} call, so FS rebuilds once. */
    private static @Nullable List<BlockPos> unlink(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        List<BlockPos> stray = strays(controller, chunks);
        if (stray != null && !stray.isEmpty()) {
            controller.addConnectedDrawers(LinkingToolItem.ActionMode.REMOVE, stray.toArray(BlockPos[]::new));
            particles(controller, stray, ParticleTypes.SMOKE);
        }
        return stray;
    }

    /**
     * Every drawer that points at this controller but is no longer connected to it - what STRICT
     * unlinks - without unlinking anything.
     *
     * <p>Drawers are found by their {@code controllerPos}, not by the controller's list: FS drops
     * unloaded drawers from the list without unlinking them (CLAUDE.md §3). Only drawers in loaded
     * chunks are candidates - removing one in an unloaded chunk would make FS load it.
     *
     * @return the stray drawers, or null if the walk met an unloaded chunk
     */
    public static @Nullable List<BlockPos> strays(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        BlockPos controllerPos = controller.getBlockPos();
        AABB range = NetworkWalker.range(controller);
        validations++;
        Set<BlockPos> connected = NetworkWalker.walk(controllerPos, range, chunks);
        if (connected == null) {
            return null;
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
        return stray;
    }

    // --- Dry run ---------------------------------------------------------------------------------

    /**
     * What STRICT would do on a set of controllers, without doing it.
     *
     * @param checked controllers whose whole network could be read
     * @param skipped controllers not readable now: their chunk, or part of their network, is not loaded
     * @param strays  drawers STRICT would unlink from the checked controllers
     */
    public record DryRun(int checked, int skipped, int strays) {
    }

    /** {@link #dryRun(ServerLevel, Collection)} on every controller the registry knows in this dimension. */
    public static DryRun dryRun(ServerLevel level) {
        return dryRun(level, List.copyOf(KNOWN.getOrDefault(level.dimension(), Set.of())));
    }

    /**
     * Counts what STRICT would unlink, in any mode, and unlinks nothing (IDEAS.md A1): what a pack
     * runs before switching an existing world to STRICT. A position that no longer holds a controller
     * is ignored.
     */
    public static DryRun dryRun(ServerLevel level, Collection<BlockPos> controllers) {
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        int checked = 0;
        int skipped = 0;
        int strays = 0;
        for (BlockPos pos : controllers) {
            LevelChunk chunk = chunks.apply(ChunkPos.asLong(pos));
            if (chunk == null) {
                skipped++;
                continue;
            }
            if (!(chunk.getBlockEntities().get(pos) instanceof StorageControllerTile<?> controller)) {
                continue;
            }
            List<BlockPos> found = strays(controller, chunks);
            if (found == null) {
                skipped++;
            } else {
                checked++;
                strays += found.size();
            }
        }
        return new DryRun(checked, skipped, strays);
    }

    /** One puff per drawer, at most {@link #PARTICLE_CAP} drawers. */
    private static void particles(StorageControllerTile<?> controller, List<BlockPos> drawers, ParticleOptions type) {
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (BlockPos pos : drawers.subList(0, Math.min(PARTICLE_CAP, drawers.size()))) {
            level.sendParticles(type, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
            particlesSent++;
        }
    }
}

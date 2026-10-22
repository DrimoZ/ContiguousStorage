package dev.drimoz.contiguousstorage.network;

import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.LinkingToolItem;
import dev.drimoz.contiguousstorage.CSConfig;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Keeps a controller's links in line with what physically touches it (CLAUDE.md §5).
 *
 * <p>Each network is known as a {@link NetworkTree}, and every member is indexed to its controller.
 * The index is what makes the hot path cheap: a block update only matters when a member just
 * vanished, which one hash lookup and one read tell. Redstone next to a network costs nothing. The
 * tree then tells most removals apart from cuts without walking anything; a real walk decides
 * whenever it cannot.
 */
public final class Networks {

    /** {@link #validate} or {@link #linkFree} could not read the whole network and changed nothing. */
    public static final int ABORTED = -1;

    /** Drawers that get a puff of particles per unlink or auto-link, so a 2 000-drawer cut does not flood a client. */
    public static final int PARTICLE_CAP = 32;

    /** How many real walks validation ran. Read by game tests; nothing else. */
    public static long validations;

    /** How many safety net checks the index answered without a walk. Read by game tests; nothing else. */
    public static long quickChecks;

    /** How many drawers got particles. Read by game test 13; nothing else. */
    public static long particlesSent;

    /**
     * Who is told what a validation unlinked, and in which words. With an English fallback: a client
     * without the lang file still reads it.
     */
    private record Report(Player player, String key, String fallback) {
        void send(int unlinked) {
            player.sendOverlayMessage(Component.translatableWithFallback(key, fallback, unlinked));
        }
    }

    /** Everything known about one dimension. */
    private static final class World {
        /** Placed this tick, and by whom (null: not a player). Linking waits for the tick (CLAUDE.md §5). */
        final Map<BlockPos, Player> placed = new HashMap<>();
        /** Controllers to walk for real at the next tick, with who to tell what it unlinks. */
        final Map<BlockPos, Report> walks = new LinkedHashMap<>();
        /** Members seen gone since the last tick, by controller. STRICT only. */
        final Long2ObjectOpenHashMap<LongArrayList> removals = new Long2ObjectOpenHashMap<>();
        /** Players who broke something next to a controller this tick, told what the break cut off. */
        final Long2ObjectOpenHashMap<Report> breakers = new Long2ObjectOpenHashMap<>();
        /** Controllers in loaded chunks: FS keeps no such list, and their tiles cannot be ticked from here. */
        final Set<BlockPos> known = new HashSet<>();
        /** Member to controller, for every tree: what the hot path reads. */
        final Long2LongOpenHashMap owner = new Long2LongOpenHashMap();
        final Long2ObjectOpenHashMap<NetworkTree> trees = new Long2ObjectOpenHashMap<>();
    }

    private static final Map<ResourceKey<Level>, World> WORLDS = new HashMap<>();

    private Networks() {
    }

    private static World world(ServerLevel level) {
        return WORLDS.computeIfAbsent(level.dimension(), key -> new World());
    }

    // --- Registry ------------------------------------------------------------------------------

    public static void chunkLoaded(ServerLevel level, LevelChunk chunk) {
        World world = world(level);
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof StorageControllerTile<?>) {
                learn(world, be.getBlockPos().immutable());
            }
        }
    }

    public static void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        World world = world(level);
        ChunkPos unloaded = chunk.getPos();
        world.known.removeIf(pos -> {
            if (ChunkPos.containing(pos).equals(unloaded)) {
                dropTree(world, pos.asLong());
                return true;
            }
            return false;
        });
    }

    /**
     * Learns the controller at {@code pos}, if there is one. Called for every placement whatever the
     * mode, and on block updates outside OFF: a controller that appeared while the world was not STRICT
     * must be known from the moment it is (found on a dedicated server).
     */
    public static void register(ServerLevel level, BlockPos pos) {
        if (blockEntity(pos, NetworkWalker.loadedChunks(level)) instanceof StorageControllerTile<?>) {
            learn(world(level), pos.immutable());
        }
    }

    /** A newly known controller is walked at the next tick, which builds its tree. */
    private static void learn(World world, BlockPos controller) {
        if (world.known.add(controller)) {
            world.walks.putIfAbsent(controller, null);
        }
    }

    // --- Detection -----------------------------------------------------------------------------

    /**
     * The hot path: a block changed at {@code pos}. It matters only if {@code pos} held a member and
     * no longer does - redstone wire updates the positions next to it, drawers included, so being a
     * member is not enough. In STRICT the removal is queued; in ASSIST the tree, now out of date, is
     * dropped, and the next placement walks for real.
     */
    public static void neighborChanged(ServerLevel level, BlockPos pos, boolean strict) {
        World world = world(level);
        long key = pos.asLong();
        if (world.owner.containsKey(key)) {
            long controller = world.owner.get(key);
            if (present(NetworkWalker.loadedChunks(level), key, controller) != Boolean.TRUE) {
                if (strict) {
                    world.removals.computeIfAbsent(controller, k -> new LongArrayList()).add(key);
                } else {
                    dropTree(world, controller);
                }
            }
        }
        register(level, pos);
    }

    /** The Linking Tool was used with this controller: walk it for real, and tell the player what that undoes. */
    public static void linkingToolUsed(ServerLevel level, BlockPos controller, Player player) {
        world(level).walks.put(controller.immutable(), new Report(player, "message.contiguousstorage.not_connected",
                "%s drawer(s) not touching the controller's network were not linked"));
    }

    /**
     * A player is breaking the block at {@code pos} (before it goes): if that cuts drawers off the
     * networks it belongs to or touches, the player is told how many.
     */
    public static void broken(ServerLevel level, BlockPos pos, Player player) {
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        Set<StorageControllerTile<?>> owners = new LinkedHashSet<>();
        addOwner(pos, chunks, owners);
        for (Direction side : NetworkWalker.DIRECTIONS) {
            addOwner(pos.relative(side), chunks, owners);
        }
        Report report = new Report(player, "message.contiguousstorage.disconnected", "%s drawer(s) disconnected");
        World world = world(level);
        owners.forEach(controller -> world.breakers.put(controller.getBlockPos().asLong(), report));
    }

    /**
     * Once per tick, outside OFF: removals are told apart from cuts by the tree (STRICT), and the
     * controllers that need it are walked for real - validated in STRICT, only re-known in ASSIST.
     */
    public static void processChanges(ServerLevel level, CSConfig.Mode mode) {
        World world = world(level);
        if (mode == CSConfig.Mode.OFF) {
            world.walks.clear();
            world.removals.clear();
            world.breakers.clear();
            return;
        }
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        boolean strict = mode == CSConfig.Mode.STRICT;

        for (var entry : world.removals.long2ObjectEntrySet()) {
            long controller = entry.getLongKey();
            NetworkTree tree = world.trees.get(controller);
            if (tree == null) {
                world.walks.putIfAbsent(BlockPos.of(controller), null);
                continue;
            }
            LongArrayList vanished = new LongArrayList();
            boolean maybeCut = false;
            for (long removed : entry.getValue()) {
                if (tree.remove(removed, member -> present(chunks, member, controller), vanished) == NetworkTree.Cut.MAYBE) {
                    maybeCut = true;
                    break;
                }
            }
            if (maybeCut) {
                world.walks.putIfAbsent(BlockPos.of(controller), null);
            } else {
                vanished.forEach(member -> unindex(world, member, controller));
            }
        }
        world.removals.clear();

        List<Map.Entry<BlockPos, Report>> walks = new ArrayList<>(world.walks.entrySet());
        world.walks.clear();
        for (Map.Entry<BlockPos, Report> walk : walks) {
            if (!(blockEntity(walk.getKey(), chunks) instanceof StorageControllerTile<?> controller)) {
                continue;
            }
            world.known.add(walk.getKey());
            if (!strict) {
                rebuild(world, controller, chunks);
                continue;
            }
            List<BlockPos> unlinked = unlink(world, controller, chunks);
            Report report = walk.getValue() != null ? walk.getValue() : world.breakers.get(walk.getKey().asLong());
            if (unlinked != null && !unlinked.isEmpty() && report != null) {
                report.send(unlinked.size());
            }
        }
        world.breakers.clear();
    }

    /**
     * The safety net, STRICT: every {@code interval} ticks, every known network is checked against
     * what the events may have missed - a member set away without a block update, a drawer linked at
     * a distance. The tree answers that by reading each member once; only a network that fails the
     * check, or has no tree yet, is walked for real.
     */
    public static void safetyNet(ServerLevel level, int interval) {
        World world = world(level);
        if (interval <= 0 || level.getGameTime() % interval != 0) {
            return;
        }
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        for (Iterator<BlockPos> it = world.known.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            LevelChunk chunk = chunks.apply(ChunkPos.pack(pos));
            if (chunk == null) {
                continue;
            }
            if (!(chunk.getBlockEntities().get(pos) instanceof StorageControllerTile<?> controller)) {
                dropTree(world, pos.asLong());
                it.remove();
                continue;
            }
            NetworkTree tree = world.trees.get(pos.asLong());
            if (tree != null && intact(tree, controller, chunks)) {
                quickChecks++;
            } else {
                unlink(world, controller, chunks);
            }
        }
    }

    /**
     * The safety net's check of one network, alone: true if its tree matches the world, false if a
     * walk is needed, null if it has no tree. For measurements; the safety net calls {@link #intact}.
     */
    public static @Nullable Boolean quickCheck(StorageControllerTile<?> controller) {
        ServerLevel level = (ServerLevel) controller.getLevel();
        NetworkTree tree = world(level).trees.get(controller.getBlockPos().asLong());
        return tree == null ? null : intact(tree, controller, NetworkWalker.loadedChunks(level));
    }

    /**
     * Whether the tree still matches the world, reading as little of it as possible.
     *
     * <p>Drawers: FS takes a drawer out of its list whenever it goes, block update or not
     * ({@code onRemove} runs whatever the flags, CLAUDE.md §3). So every drawer FS lists must be a
     * linked drawer of the tree - which also catches one linked at a distance - and a linked drawer FS
     * no longer lists is either unloaded or gone: only those few are read. Free drawers and conductors
     * FS knows nothing about; they are read one by one.
     */
    private static boolean intact(NetworkTree tree, StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        List<Long> listed = controller.getConnectedDrawers().getConnectedDrawers();
        LongOpenHashSet inList = new LongOpenHashSet(listed.size());
        for (Long drawer : listed) {
            if (!tree.linked.contains(drawer.longValue())) {
                return false;
            }
            inList.add(drawer.longValue());
        }
        for (long drawer : tree.linked) {
            if (!inList.contains(drawer) && present(chunks, drawer, tree.controller) == Boolean.FALSE) {
                return false;
            }
        }
        for (long member : tree.unlisted) {
            if (present(chunks, member, tree.controller) == Boolean.FALSE) {
                return false;
            }
        }
        return true;
    }

    // --- Auto-link -----------------------------------------------------------------------------

    public static void placed(ServerLevel level, BlockPos pos, @Nullable Player player) {
        world(level).placed.put(pos.immutable(), player);
    }

    /**
     * Auto-link for everything placed since the last tick. A placed controller links every free
     * drawer it reaches. A placed drawer or conductor touching a known network grows it: only the
     * free drawers and conductors it newly connects are walked and linked, so the cost is what was
     * placed, not the size of the network. Without a known network next to it, the old way: find the
     * controller - along conductors if needed - and walk it.
     *
     * <p>Only for a controller the placer may interact with (IDEAS.md §4); a placement by anything
     * but a player links nothing.
     */
    public static void linkPlaced(ServerLevel level) {
        World world = world(level);
        if (world.placed.isEmpty()) {
            return;
        }
        Map<BlockPos, Player> placed = new HashMap<>(world.placed);
        world.placed.clear();
        LongFunction<LevelChunk> chunks = NetworkWalker.loadedChunks(level);
        Set<StorageControllerTile<?>> fullLinks = new LinkedHashSet<>();
        placed.forEach((pos, player) -> {
            if (player == null) {
                return;
            }
            BlockEntity be = blockEntity(pos, chunks);
            if (be instanceof StorageControllerTile<?> controller) {
                if (level.mayInteract(player, pos)) {
                    fullLinks.add(controller);
                }
                return;
            }
            LevelChunk chunk = chunks.apply(ChunkPos.pack(pos));
            if (chunk == null || !NetworkWalker.isMember(chunk, pos, pos)) {
                return;
            }
            Map<StorageControllerTile<?>, Long> anchors = new LinkedHashMap<>();
            for (Direction side : NetworkWalker.DIRECTIONS) {
                long neighbour = BlockPos.offset(pos.asLong(), side);
                BlockEntity there = blockEntity(BlockPos.of(neighbour), chunks);
                if (there instanceof StorageControllerTile<?> controller) {
                    anchors.putIfAbsent(controller, neighbour);
                } else if (world.owner.containsKey(neighbour)) {
                    long owner = world.owner.get(neighbour);
                    if (present(chunks, neighbour, owner) == Boolean.TRUE
                            && blockEntity(BlockPos.of(owner), chunks) instanceof StorageControllerTile<?> controller) {
                        anchors.putIfAbsent(controller, neighbour);
                    }
                }
            }
            if (anchors.isEmpty()) {
                Set<StorageControllerTile<?>> owners = new LinkedHashSet<>();
                addOwners(pos, chunks, owners);
                owners.removeIf(controller -> !level.mayInteract(player, controller.getBlockPos()));
                fullLinks.addAll(owners);
                return;
            }
            anchors.forEach((controller, anchor) -> {
                if (!level.mayInteract(player, controller.getBlockPos())) {
                    return;
                }
                NetworkTree tree = world.trees.get(controller.getBlockPos().asLong());
                if (tree == null || !grow(world, controller, tree, pos, anchor, chunks)) {
                    fullLinks.add(controller);
                }
            });
        });
        fullLinks.forEach(controller -> link(world, controller, chunks));
    }

    /**
     * Grows a known network from {@code start}, placed against {@code anchor}: walks only what is not
     * in the tree yet - free drawers and conductors - and links its free drawers in one ADD.
     *
     * @return false if a chunk on the way was not loaded: the caller walks the whole network instead
     */
    private static boolean grow(World world, StorageControllerTile<?> controller, NetworkTree tree, BlockPos start, long anchor,
                                LongFunction<LevelChunk> chunks) {
        BlockPos controllerPos = controller.getBlockPos();
        long controllerKey = controllerPos.asLong();
        AABB range = NetworkWalker.range(controller);
        LongOpenHashSet visited = new LongOpenHashSet();
        Long2LongOpenHashMap parentOf = new Long2LongOpenHashMap();
        Long2LongOpenHashMap joined = new Long2LongOpenHashMap();
        LongOpenHashSet joinedLinked = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        visited.add(start.asLong());
        parentOf.put(start.asLong(), anchor);
        queue.enqueue(start.asLong());
        List<BlockPos> free = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            long pos = queue.dequeueLong();
            cursor.set(pos);
            if (!range.contains(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5)) {
                continue;
            }
            LevelChunk chunk = chunks.apply(ChunkPos.pack(cursor));
            if (chunk == null) {
                return false;
            }
            if (!NetworkWalker.isMember(chunk, cursor, controllerPos)) {
                continue;
            }
            if (!tree.contains(pos)) {
                joined.put(pos, parentOf.get(pos));
            }
            if (chunk.getBlockEntities().get(cursor) instanceof ControllableDrawerTile<?> drawer) {
                if (drawer.getControllerPos() == null) {
                    free.add(cursor.immutable());
                } else {
                    joinedLinked.add(pos);
                }
            }
            for (Direction side : NetworkWalker.DIRECTIONS) {
                long next = BlockPos.offset(pos, side);
                if (next != controllerKey && !tree.contains(next) && visited.add(next)) {
                    parentOf.put(next, pos);
                    queue.enqueue(next);
                }
            }
        }
        joined.long2LongEntrySet().forEach(entry -> {
            long member = entry.getLongKey();
            tree.add(member, entry.getLongValue());
            if (joinedLinked.contains(member)) {
                tree.linked.add(member);
            } else {
                tree.unlisted.add(member);
            }
            world.owner.put(member, controllerKey);
        });
        if (!free.isEmpty()) {
            controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, free.toArray(BlockPos[]::new));
            free.forEach(drawer -> tree.markLinked(drawer.asLong()));
            particles(controller, free, ParticleTypes.HAPPY_VILLAGER);
        }
        return true;
    }

    /** @return how many drawers were linked, or {@link #ABORTED} if the walk met an unloaded chunk */
    public static int linkFree(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        List<BlockPos> linked = link(world((ServerLevel) controller.getLevel()), controller, chunks);
        return linked == null ? ABORTED : linked.size();
    }

    /**
     * Walks the whole network and links every free drawer it reaches, in one {@code ADD}. A drawer
     * that already has a controller keeps it: first one wins (DESIGN.md §4), and moving it would make
     * FS look up its old controller with a blocking read (CLAUDE.md §3).
     *
     * @return the drawers linked, or null if the walk met an unloaded chunk
     */
    private static @Nullable List<BlockPos> link(World world, StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        NetworkWalker.Walk walk = NetworkWalker.walkTree(controller.getBlockPos(), NetworkWalker.range(controller), chunks);
        if (walk == null) {
            return null;
        }
        List<BlockPos> free = walk.free().longStream().mapToObj(BlockPos::of).toList();
        NetworkTree tree = setTree(world, controller, walk);
        if (!free.isEmpty()) {
            controller.addConnectedDrawers(LinkingToolItem.ActionMode.ADD, free.toArray(BlockPos[]::new));
            walk.free().forEach(tree::markLinked);
            particles(controller, free, ParticleTypes.HAPPY_VILLAGER);
        }
        return free;
    }

    /** How many conductor blocks {@link #addOwners} goes through before giving up. */
    private static final int CONDUCTOR_SEARCH_LIMIT = 512;

    /**
     * The owners of {@code start} and of its neighbours - and, since a conductor carries no
     * {@code controllerPos}, of everything along the conductor blocks it touches. Only used when no
     * known network touches the placed block.
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
            for (Direction side : NetworkWalker.DIRECTIONS) {
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
        LevelChunk chunk = chunks.apply(ChunkPos.pack(pos));
        return chunk != null && NetworkWalker.isConductor(chunk.getBlockState(pos));
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

    // --- Validation ----------------------------------------------------------------------------

    public static int validate(ServerLevel level, StorageControllerTile<?> controller) {
        return validate(controller, NetworkWalker.loadedChunks(level));
    }

    /** @return how many drawers were unlinked, or {@link #ABORTED} if the walk met an unloaded chunk */
    public static int validate(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        List<BlockPos> unlinked = unlink(world((ServerLevel) controller.getLevel()), controller, chunks);
        return unlinked == null ? ABORTED : unlinked.size();
    }

    /**
     * A real walk: unlinks every stray drawer in one {@code addConnectedDrawers(REMOVE, …)} call, so
     * FS rebuilds once, and the walk becomes the network's tree.
     */
    private static @Nullable List<BlockPos> unlink(World world, StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        NetworkWalker.Walk walk = NetworkWalker.walkTree(controller.getBlockPos(), NetworkWalker.range(controller), chunks);
        validations++;
        if (walk == null) {
            return null;
        }
        List<BlockPos> stray = strays(controller, walk.parent(), chunks);
        setTree(world, controller, walk);
        if (!stray.isEmpty()) {
            controller.addConnectedDrawers(LinkingToolItem.ActionMode.REMOVE, stray.toArray(BlockPos[]::new));
            particles(controller, stray, ParticleTypes.SMOKE);
        }
        return stray;
    }

    /** Walks without changing anything, and builds the network's tree. ASSIST: nothing to unlink. */
    private static void rebuild(World world, StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        NetworkWalker.Walk walk = NetworkWalker.walkTree(controller.getBlockPos(), NetworkWalker.range(controller), chunks);
        if (walk != null) {
            setTree(world, controller, walk);
        }
    }

    /**
     * Every drawer that points at this controller but is no longer connected to it - what STRICT
     * unlinks - without unlinking anything.
     *
     * @return the stray drawers, or null if the walk met an unloaded chunk
     */
    public static @Nullable List<BlockPos> strays(StorageControllerTile<?> controller, LongFunction<LevelChunk> chunks) {
        NetworkWalker.Walk walk = NetworkWalker.walkTree(controller.getBlockPos(), NetworkWalker.range(controller), chunks);
        validations++;
        return walk == null ? null : strays(controller, walk.parent(), chunks);
    }

    /**
     * Drawers are found by their {@code controllerPos}, not by the controller's list: FS drops unloaded
     * drawers from the list without unlinking them (CLAUDE.md §3). Only drawers in loaded chunks are
     * candidates - removing one in an unloaded chunk would make FS load it.
     */
    private static List<BlockPos> strays(StorageControllerTile<?> controller, Long2LongOpenHashMap walked, LongFunction<LevelChunk> chunks) {
        BlockPos controllerPos = controller.getBlockPos();
        AABB range = NetworkWalker.range(controller);
        List<BlockPos> stray = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(range.minX); cx <= SectionPos.blockToSectionCoord(range.maxX); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(range.minZ); cz <= SectionPos.blockToSectionCoord(range.maxZ); cz++) {
                LevelChunk chunk = chunks.apply(ChunkPos.pack(cx, cz));
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ControllableDrawerTile<?> drawer
                            && !(be instanceof StorageControllerTile<?>)
                            && controllerPos.equals(drawer.getControllerPos())
                            && !walked.containsKey(be.getBlockPos().asLong())) {
                        stray.add(be.getBlockPos());
                    }
                }
            }
        }
        return stray;
    }

    // --- Index ---------------------------------------------------------------------------------

    private static NetworkTree setTree(World world, StorageControllerTile<?> controller, NetworkWalker.Walk walk) {
        long key = controller.getBlockPos().asLong();
        dropTree(world, key);
        NetworkTree tree = new NetworkTree(key, walk);
        world.trees.put(key, tree);
        walk.parent().keySet().forEach(member -> world.owner.put(member, key));
        return tree;
    }

    private static void dropTree(World world, long controller) {
        NetworkTree tree = world.trees.remove(controller);
        if (tree != null) {
            tree.members().forEach(member -> unindex(world, member, controller));
        }
    }

    private static void unindex(World world, long member, long controller) {
        if (world.owner.containsKey(member) && world.owner.get(member) == controller) {
            world.owner.remove(member);
        }
    }

    /** Whether {@code member} still carries {@code controller}'s network: TRUE, FALSE, or null if not loaded. */
    private static @Nullable Boolean present(LongFunction<LevelChunk> chunks, long member, long controller) {
        BlockPos pos = BlockPos.of(member);
        LevelChunk chunk = chunks.apply(ChunkPos.pack(pos));
        return chunk == null ? null : NetworkWalker.isMember(chunk, pos, BlockPos.of(controller));
    }

    private static @Nullable BlockEntity blockEntity(BlockPos pos, LongFunction<LevelChunk> chunks) {
        LevelChunk chunk = chunks.apply(ChunkPos.pack(pos));
        return chunk == null ? null : chunk.getBlockEntities().get(pos);
    }

    // --- Dry run -------------------------------------------------------------------------------

    /**
     * What STRICT would do on a set of controllers, without doing it.
     *
     * @param checked controllers whose whole network could be read
     * @param skipped controllers not readable now: their chunk, or part of their network, is not loaded
     * @param strays  drawers STRICT would unlink from the checked controllers
     */
    public record DryRun(int checked, int skipped, int strays) {
    }

    /** {@link #dryRun(ServerLevel, Collection)} on every controller known in this dimension. */
    public static DryRun dryRun(ServerLevel level) {
        return dryRun(level, List.copyOf(world(level).known));
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
            LevelChunk chunk = chunks.apply(ChunkPos.pack(pos));
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

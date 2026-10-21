package dev.drimoz.contiguousstorage.network;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.function.LongFunction;

/**
 * One controller's network as known: every member (drawer or conductor, {@link BlockPos#asLong})
 * and the member it was reached from, a breadth-first tree rooted at the controller.
 *
 * <p>It only ever answers "nothing was cut" quickly. Whenever a removal might have cut something,
 * the caller runs a real walk, which alone decides what to unlink: a stale tree can delay a cut,
 * never invent one.
 */
final class NetworkTree {

    /** What a removal did to the network, as far as the tree can tell. */
    enum Cut {
        /** Every other member still has a path to the controller. */
        NONE,
        /** Something may be cut off, or the tree cannot tell: run a real walk. */
        MAYBE
    }

    final long controller;
    private final Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
    private final Long2ObjectOpenHashMap<LongArrayList> children = new Long2ObjectOpenHashMap<>();
    /** Members that are drawers linked to this controller: what FS lists, when they are loaded. */
    final LongOpenHashSet linked = new LongOpenHashSet();
    /** Members FS knows nothing about - free drawers and conductors - which only reading them can check. */
    final LongOpenHashSet unlisted = new LongOpenHashSet();

    NetworkTree(long controller, NetworkWalker.Walk walk) {
        this.controller = controller;
        walk.parent().long2LongEntrySet().forEach(entry -> add(entry.getLongKey(), entry.getLongValue()));
        linked.addAll(walk.linked());
        unlisted.addAll(walk.free());
        unlisted.addAll(walk.conductors());
    }

    boolean contains(long member) {
        return parent.containsKey(member);
    }

    /** {@code member}, a free drawer of the network, has just been linked to this controller. */
    void markLinked(long member) {
        unlisted.remove(member);
        linked.add(member);
    }

    int size() {
        return parent.size();
    }

    LongOpenHashSet members() {
        return new LongOpenHashSet(parent.keySet());
    }

    void add(long member, long from) {
        parent.put(member, from);
        children.computeIfAbsent(from, key -> new LongArrayList()).add(member);
    }

    /**
     * Takes {@code removed} out of the network. A member reached through it - its subtree - is
     * reattached when it touches a member outside the subtree that is still there; the rest of the
     * subtree follows from those. Removing a member nothing was reached through costs nothing.
     *
     * @param present  whether a position still holds a member: TRUE, FALSE, or null if its chunk is
     *                 not loaded
     * @param vanished filled with the members dropped from the tree: {@code removed}, and subtree
     *                 members that are gone too
     */
    Cut remove(long removed, LongFunction<Boolean> present, LongArrayList vanished) {
        if (!parent.containsKey(removed)) {
            return Cut.NONE;
        }
        detach(removed);
        vanished.add(removed);
        LongArrayList reachedThrough = children.remove(removed);
        if (reachedThrough == null || reachedThrough.isEmpty()) {
            return Cut.NONE;
        }

        LongOpenHashSet orphans = new LongOpenHashSet();
        LongArrayList stack = new LongArrayList(reachedThrough);
        while (!stack.isEmpty()) {
            long member = stack.popLong();
            if (orphans.add(member)) {
                LongArrayList below = children.get(member);
                if (below != null) {
                    stack.addAll(below);
                }
            }
        }

        // Orphans touching a member outside the subtree, or the controller, hang there again.
        LongOpenHashSet reattached = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet gone = new LongOpenHashSet();
        for (long orphan : orphans) {
            Boolean here = present.apply(orphan);
            if (here == null) {
                return Cut.MAYBE;
            }
            if (!here) {
                gone.add(orphan);
                continue;
            }
            for (Direction side : NetworkWalker.DIRECTIONS) {
                long neighbour = BlockPos.offset(orphan, side);
                if (neighbour == removed || orphans.contains(neighbour)) {
                    continue;
                }
                if (neighbour == controller || parent.containsKey(neighbour)) {
                    Boolean there = neighbour == controller ? Boolean.TRUE : present.apply(neighbour);
                    if (there == null) {
                        return Cut.MAYBE;
                    }
                    if (there) {
                        reparent(orphan, neighbour);
                        reattached.add(orphan);
                        queue.enqueue(orphan);
                        break;
                    }
                }
            }
        }
        // The rest of the subtree follows from the reattached ones.
        while (!queue.isEmpty()) {
            long from = queue.dequeueLong();
            for (Direction side : NetworkWalker.DIRECTIONS) {
                long neighbour = BlockPos.offset(from, side);
                if (orphans.contains(neighbour) && !reattached.contains(neighbour) && !gone.contains(neighbour)) {
                    reparent(neighbour, from);
                    reattached.add(neighbour);
                    queue.enqueue(neighbour);
                }
            }
        }

        if (reattached.size() + gone.size() < orphans.size()) {
            return Cut.MAYBE;
        }
        for (long member : gone) {
            detach(member);
            children.remove(member);
            vanished.add(member);
        }
        return Cut.NONE;
    }

    private void detach(long member) {
        linked.remove(member);
        unlisted.remove(member);
        long from = parent.remove(member);
        LongArrayList siblings = children.get(from);
        if (siblings != null) {
            siblings.rem(member);
        }
    }

    private void reparent(long member, long from) {
        boolean wasLinked = linked.contains(member);
        boolean wasUnlisted = unlisted.contains(member);
        detach(member);
        add(member, from);
        if (wasLinked) {
            linked.add(member);
        }
        if (wasUnlisted) {
            unlisted.add(member);
        }
    }
}

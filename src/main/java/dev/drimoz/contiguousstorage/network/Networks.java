package dev.drimoz.contiguousstorage.network;

import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.buuz135.functionalstorage.item.LinkingToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Keeps a controller's links in line with what physically touches it (CLAUDE.md §5).
 */
public final class Networks {

    /** {@link #validate} could not read the whole network and removed nothing. */
    public static final int ABORTED = -1;

    private Networks() {
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

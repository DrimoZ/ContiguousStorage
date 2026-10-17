package dev.drimoz.contiguousstorage.event;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.item.FSAttachments;
import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import dev.drimoz.contiguousstorage.network.Networks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Every listener of the mod, on the game bus (CLAUDE.md §5). */
@EventBusSubscriber(modid = ContiguousStorage.MOD_ID)
public final class CSEvents {

    private CSEvents() {
    }

    /** Also receives {@code EntityMultiPlaceEvent}; its main position is enough for a drawer. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // Whatever the mode: a controller placed while OFF must be known if the world goes STRICT.
            Networks.register(level, event.getPos());
            if (CSConfig.mode() != CSConfig.Mode.OFF) {
                Networks.placed(level, event.getPos(), event.getEntity() instanceof Player player ? player : null);
            }
        }
    }

    /**
     * Runs on every block update of the server, and on the client too. OFF and the client leave at
     * the first test. ASSIST only notes a controller set at that position (one map lookup), so that
     * it is on the safety net if the world goes STRICT; only STRICT looks at the neighbours.
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        CSConfig.Mode mode = CSConfig.mode();
        if (mode == CSConfig.Mode.OFF || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (mode == CSConfig.Mode.STRICT) {
            Networks.neighborChanged(level, event.getPos());
        } else {
            Networks.register(level, event.getPos());
        }
    }

    /**
     * The Linking Tool calls {@code addConnectedDrawers} directly; nothing comes out of it. In
     * STRICT, let it link, then validate its controller at the next tick and tell the player what
     * was undone - one path for both its single and its area mode (DESIGN.md §4). Fires before
     * {@code useOn}, on both sides.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (CSConfig.mode() == CSConfig.Mode.STRICT && event.getLevel() instanceof ServerLevel level) {
            ItemStack stack = event.getItemStack();
            BlockPos controller = stack.is(FunctionalStorage.LINKING_TOOL.get()) ? stack.get(FSAttachments.CONTROLLER) : null;
            if (controller != null) {
                Networks.markDirty(level, controller, event.getEntity());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Networks.linkPlaced(level);
            if (CSConfig.mode() == CSConfig.Mode.STRICT) {
                Networks.validateDirty(level);
                Networks.safetyNet(level, CSConfig.VALIDATION_INTERVAL_TICKS.get());
            }
        }
    }

    /**
     * Server thread, before the chunk is necessarily FULL: only the positions of its controllers
     * are read, nothing else is asked of the level (CLAUDE.md §5).
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            Networks.chunkLoaded(level, chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            Networks.chunkUnloaded(level, chunk);
        }
    }
}

package dev.drimoz.contiguousstorage.event;

import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import dev.drimoz.contiguousstorage.network.Networks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
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
        if (event.getLevel() instanceof ServerLevel level && CSConfig.mode() != CSConfig.Mode.OFF) {
            Networks.placed(level, event.getPos());
        }
    }

    /**
     * Runs on every block update of the server, and on the client too. Only STRICT ever unlinks, so
     * every other case - OFF, ASSIST, the client - leaves at the first test.
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (CSConfig.mode() == CSConfig.Mode.STRICT && event.getLevel() instanceof ServerLevel level) {
            Networks.neighborChanged(level, event.getPos());
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

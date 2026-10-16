package dev.drimoz.contiguousstorage.event;

import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import dev.drimoz.contiguousstorage.network.Networks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
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

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Networks.linkPlaced(level);
        }
    }
}

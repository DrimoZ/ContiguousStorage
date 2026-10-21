package dev.drimoz.contiguousstorage.event;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.item.FSAttachments;
import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import dev.drimoz.contiguousstorage.network.Networks;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
     * the first test. Otherwise one hash lookup tells whether the changed position was a member of a
     * known network, and one read whether it still is - redstone next to a network costs that and
     * nothing more.
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        CSConfig.Mode mode = CSConfig.mode();
        if (mode != CSConfig.Mode.OFF && event.getLevel() instanceof ServerLevel level) {
            Networks.neighborChanged(level, event.getPos(), mode == CSConfig.Mode.STRICT);
        }
    }

    /**
     * Fires before the block goes, player only. In STRICT, the controllers it belongs to or touches
     * are checked at the next tick, and the player is told how many drawers the break cut off. A
     * break another mod cancels afterwards only costs a validation that finds nothing.
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (CSConfig.mode() == CSConfig.Mode.STRICT && event.getLevel() instanceof ServerLevel level) {
            Networks.broken(level, event.getPos(), event.getPlayer());
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
                Networks.linkingToolUsed(level, controller, event.getEntity());
            }
        }
    }

    /**
     * {@code /contiguousstorage dryrun}: what STRICT would unlink in this dimension, in any mode,
     * without unlinking anything. Brigadier runs server-side, so it works from a vanilla client.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ContiguousStorage.MOD_ID)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("dryrun").executes(context -> {
                    Networks.DryRun result = Networks.dryRun(context.getSource().getLevel());
                    context.getSource().sendSuccess(() -> Component.translatableWithFallback(
                            "commands.contiguousstorage.dryrun",
                            "%s controller(s) checked, %s skipped (part of their network is not loaded): "
                                    + "STRICT would unlink %s drawer(s). Nothing was changed.",
                            result.checked(), result.skipped(), result.strays()), false);
                    return result.strays();
                })));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CSConfig.Mode mode = CSConfig.mode();
            Networks.linkPlaced(level);
            Networks.processChanges(level, mode);
            if (mode == CSConfig.Mode.STRICT) {
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

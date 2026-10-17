package dev.drimoz.contiguousstorage.mixin;

import com.buuz135.functionalstorage.block.tile.ControllableDrawerTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Backport of Functional Storage's own fix (branch 1.21, unreleased) for a server hang in 1.5.7.
 *
 * <p>When a drawer's chunk finishes loading, {@code invalidateCapabilities} reads the controller's
 * block entity with {@code Level.getBlockEntity}. If the controller sits in another chunk that is
 * loaded but not yet ready, that read waits for it - from inside the very task that would make it
 * ready - and the server thread parks forever (reproduced on a dedicated server, MANUAL_TESTS.md).
 * {@code onChunkUnloaded} does the same read.
 *
 * <p>Here the read never waits: a controller whose chunk is not ready is treated as absent, which in
 * both methods means "skip the rebuild" - exactly what FS does when the controller is not loaded.
 * {@code onLoad} is left alone: it runs at block entity tick, outside chunk loading, and there a
 * null would clear the drawer's {@code controllerPos}.
 *
 * <p>Applied to FS 1.5.7 and older only ({@link CSMixinPlugin}). To delete once FS ships its fix.
 */
@Mixin(value = ControllableDrawerTile.class, remap = false)
public abstract class ControllableDrawerTileMixin {

    @Redirect(method = {"invalidateCapabilities", "onChunkUnloaded"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity contiguousstorage$getBlockEntityIfReady(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return level.getBlockEntity(pos);
        }
        LevelChunk chunk = server.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        return chunk == null ? null : chunk.getBlockEntities().get(pos);
    }
}

package dev.drimoz.contiguousstorage.block;

import com.buuz135.functionalstorage.block.FramedDrawerBlock;
import com.buuz135.functionalstorage.client.model.FramedDrawerModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The framed trim, drawn by Functional Storage's framed model loader: frame from the first block of
 * {@link FramedTrimRecipe}, core from the second. Its look lives in {@link FramedTrimTile}.
 */
public class FramedTrimBlock extends TrimBlock implements EntityBlock {

    public FramedTrimBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FramedTrimTile(pos, state);
    }

    /** The item's {@code Style}, read the way Functional Storage reads its framed drawers'. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        FramedDrawerModelData design = FramedDrawerBlock.getDrawerModelData(stack);
        if (design != null && level.getBlockEntity(pos) instanceof FramedTrimTile tile) {
            tile.setDesign(design);
        }
    }

    /** Pick-block keeps the look. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        if (level.getBlockEntity(pos) instanceof FramedTrimTile tile && !tile.getDesign().getDesign().isEmpty()) {
            stack.getOrCreateTag().put(FramedTrimTile.STYLE, tile.getDesign().serializeNBT());
        }
        return stack;
    }
}

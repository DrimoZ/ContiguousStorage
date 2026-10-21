package dev.drimoz.contiguousstorage.block;

import com.buuz135.functionalstorage.block.FramedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The framed trim. {@link FramedBlock} is what Functional Storage's own framed recipe looks for, so
 * the trim is dressed exactly like a framed drawer: frame from the first block, core from the
 * second. Its look lives in {@link FramedTrimTile}.
 */
public class FramedTrimBlock extends TrimBlock implements EntityBlock, FramedBlock {

    public FramedTrimBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FramedTrimTile(pos, state);
    }

    /** Pick-block keeps the look. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof FramedTrimTile tile) {
            stack.applyComponents(tile.collectComponents());
        }
        return stack;
    }
}

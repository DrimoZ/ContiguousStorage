package dev.drimoz.contiguousstorage.block;

import net.minecraft.world.level.block.Block;

/**
 * A trim: a decorative block that carries a Functional Storage network without being linked to it,
 * like Storage Drawers' trims. One per Functional Storage wood ({@link CSBlocks}). No block entity:
 * nothing to tick, nothing to save.
 */
public class TrimBlock extends Block {

    public TrimBlock(Properties properties) {
        super(properties);
    }
}

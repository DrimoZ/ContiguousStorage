package dev.drimoz.contiguousstorage.block;

import com.buuz135.functionalstorage.client.model.FramedDrawerModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.HashMap;

/**
 * What a framed trim looks like - Functional Storage's {@link FramedDrawerModelData}, keyed like a
 * framed drawer's style ({@code side}, {@code front}, {@code particle}). It travels in the item's
 * {@code Style} tag, as on Functional Storage's framed drawers: applied when placed, kept when broken
 * or picked.
 */
public class FramedTrimTile extends BlockEntity {

    /** The tag holding the look, on the item and in the block entity alike. */
    public static final String STYLE = "Style";

    private FramedDrawerModelData design = new FramedDrawerModelData(new HashMap<>());

    public FramedTrimTile(BlockPos pos, BlockState state) {
        super(CSBlocks.FRAMED_TRIM_TILE, pos, state);
    }

    public FramedDrawerModelData getDesign() {
        return design;
    }

    public void setDesign(FramedDrawerModelData design) {
        this.design = design;
        setChanged();
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(FramedDrawerModelData.FRAMED_PROPERTY, design).build();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(STYLE, design.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        FramedDrawerModelData loaded = new FramedDrawerModelData(new HashMap<>());
        loaded.deserializeNBT(tag.getCompound(STYLE));
        design = loaded;
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

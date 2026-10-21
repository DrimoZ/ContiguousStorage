package dev.drimoz.contiguousstorage.block;

import com.buuz135.functionalstorage.block.tile.FramedTile;
import com.buuz135.functionalstorage.client.model.FramedDrawerModelData;
import com.buuz135.functionalstorage.item.FSAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.Map;

/**
 * What a framed trim looks like - Functional Storage's {@link FramedDrawerModelData}, keyed like a
 * framed drawer's style ({@code side}, {@code front}, {@code particle}). It travels in the item's
 * {@code functionalstorage:style} component: applied when placed, collected when broken or picked.
 */
public class FramedTrimTile extends BlockEntity implements FramedTile {

    private FramedDrawerModelData design = new FramedDrawerModelData(new HashMap<>());

    public FramedTrimTile(BlockPos pos, BlockState state) {
        super(CSBlocks.FRAMED_TRIM_TILE, pos, state);
    }

    @Override
    public FramedDrawerModelData getFramedDrawerModelData() {
        return design;
    }

    @Override
    public void setFramedDrawerModelData(FramedDrawerModelData design) {
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("design", style());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        design = fromStyle(tag.getCompound("design"));
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        CompoundTag style = input.get(FSAttachments.STYLE.get());
        if (style != null) {
            design = fromStyle(style);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        if (!design.getDesign().isEmpty()) {
            components.set(FSAttachments.STYLE.get(), style());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("design");
    }

    private CompoundTag style() {
        CompoundTag style = new CompoundTag();
        design.getDesign().forEach((key, item) -> style.putString(key, BuiltInRegistries.ITEM.getKey(item).toString()));
        return style;
    }

    private static FramedDrawerModelData fromStyle(CompoundTag style) {
        Map<String, Item> design = new HashMap<>();
        for (String key : style.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(style.getString(key));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                design.put(key, BuiltInRegistries.ITEM.get(id));
            }
        }
        return new FramedDrawerModelData(design);
    }
}

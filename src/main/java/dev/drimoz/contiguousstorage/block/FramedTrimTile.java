package dev.drimoz.contiguousstorage.block;

import com.buuz135.functionalstorage.block.tile.FramedTile;
import com.buuz135.functionalstorage.item.FSAttachments;
import com.buuz135.functionalstorage.util.CustomFramedDrawerModelData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;

import java.util.HashMap;
import java.util.Map;

/**
 * What a framed trim looks like - Functional Storage's {@link CustomFramedDrawerModelData}, keyed like a
 * framed drawer's style ({@code side}, {@code front}, {@code particle}). It travels in the item's
 * {@code functionalstorage:style} component: applied when placed, collected when broken or picked.
 */
public class FramedTrimTile extends BlockEntity implements FramedTile {

    private CustomFramedDrawerModelData design = new CustomFramedDrawerModelData(new HashMap<>());

    public FramedTrimTile(BlockPos pos, BlockState state) {
        super(CSBlocks.FRAMED_TRIM_TILE, pos, state);
    }

    @Override
    public CustomFramedDrawerModelData getFramedDrawerModelData() {
        return design;
    }

    @Override
    public void setFramedDrawerModelData(CustomFramedDrawerModelData design) {
        this.design = design;
        setChanged();
        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.of(CustomFramedDrawerModelData.MODEL_PROPERTY, design);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("design", CompoundTag.CODEC, style());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        design = fromStyle(input.read("design", CompoundTag.CODEC).orElseGet(CompoundTag::new));
        if (level != null && level.isClientSide()) {
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
    protected void applyImplicitComponents(DataComponentGetter components) {
        CompoundTag style = components.get(FSAttachments.STYLE.get());
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
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("design");
    }

    private CompoundTag style() {
        CompoundTag style = new CompoundTag();
        design.getDesign().forEach((key, item) -> style.putString(key, BuiltInRegistries.ITEM.getKey(item).toString()));
        return style;
    }

    private static CustomFramedDrawerModelData fromStyle(CompoundTag style) {
        Map<String, Item> design = new HashMap<>();
        for (String key : style.keySet()) {
            style.getString(key).map(Identifier::tryParse).flatMap(BuiltInRegistries.ITEM::getOptional)
                    .ifPresent(item -> design.put(key, item));
        }
        return new CustomFramedDrawerModelData(design);
    }
}

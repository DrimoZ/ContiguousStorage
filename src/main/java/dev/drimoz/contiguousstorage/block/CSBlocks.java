package dev.drimoz.contiguousstorage.block;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.util.DrawerWoodType;
import com.buuz135.functionalstorage.util.IWoodType;
import com.mojang.logging.LogUtils;
import dev.drimoz.contiguousstorage.CSConfig;
import dev.drimoz.contiguousstorage.ContiguousStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The trims: one per wood Functional Storage makes drawers of, and a framed one.
 *
 * <p>The woods are read from {@code FunctionalStorage.WOOD_TYPES} when blocks are registered, not
 * when this mod is built: a Functional Storage addon that adds a wood gets its trim without a line
 * here. Registered at the lowest priority, so that an addon's planks already exist. The blocks are
 * always registered, whatever {@link CSConfig#TRIMS_ENABLED} says - clients and server must agree on
 * the block list; the config only takes the recipes, the creative tab entries and the conduction away.
 */
public final class CSBlocks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Each wood trim and the wood it is made of, in Functional Storage's order. */
    public static final Map<TrimBlock, IWoodType> WOOD_TRIMS = new LinkedHashMap<>();
    public static FramedTrimBlock FRAMED_TRIM;
    public static BlockEntityType<FramedTrimTile> FRAMED_TRIM_TILE;

    private CSBlocks() {
    }

    public static List<TrimBlock> all() {
        List<TrimBlock> all = new ArrayList<>(WOOD_TRIMS.keySet());
        if (FRAMED_TRIM != null) {
            all.add(FRAMED_TRIM);
        }
        return all;
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ContiguousStorage.MOD_ID, path);
    }

    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            for (IWoodType wood : FunctionalStorage.WOOD_TYPES) {
                if (wood == DrawerWoodType.FRAMED) {
                    continue;
                }
                try {
                    TrimBlock trim = new TrimBlock(BlockBehaviour.Properties.ofFullCopy(wood.getPlanks()));
                    helper.register(id(wood.getName() + "_trim"), trim);
                    WOOD_TRIMS.put(trim, wood);
                } catch (RuntimeException e) {
                    LOGGER.warn("No trim for Functional Storage wood {}: {}", wood.getName(), e.toString());
                }
            }
            FRAMED_TRIM = new FramedTrimBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));
            helper.register(id("framed_trim"), FRAMED_TRIM);
        });
        event.register(Registries.ITEM, helper ->
                all().forEach(trim -> helper.register(BuiltInRegistries.BLOCK.getKey(trim), new BlockItem(trim, new Item.Properties()))));
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            FRAMED_TRIM_TILE = BlockEntityType.Builder.of(FramedTrimTile::new, FRAMED_TRIM).build(null);
            helper.register(id("framed_trim"), FRAMED_TRIM_TILE);
        });
    }

    /** In Functional Storage's tab and in Building Blocks - unless trims are turned off. */
    public static void fillCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        boolean ours = event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS
                || event.getTabKey().location().equals(FunctionalStorage.TAB.getResourceLocation());
        if (ours && CSConfig.TRIMS_ENABLED.get()) {
            all().forEach(event::accept);
        }
    }
}

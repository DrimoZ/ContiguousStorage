package dev.drimoz.contiguousstorage;

import dev.drimoz.contiguousstorage.block.CSBlocks;
import dev.drimoz.contiguousstorage.block.TrimPack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ContiguousStorage.MOD_ID)
public final class ContiguousStorage {

    public static final String MOD_ID = "contiguousstorage";

    public ContiguousStorage() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CSConfig.SPEC);
        // Lowest: a Functional Storage addon's wood, and its planks, are registered by then.
        modBus.addListener(EventPriority.LOWEST, CSBlocks::register);
        modBus.addListener(TrimPack::addPacks);
        modBus.addListener(CSBlocks::fillCreativeTabs);
    }
}

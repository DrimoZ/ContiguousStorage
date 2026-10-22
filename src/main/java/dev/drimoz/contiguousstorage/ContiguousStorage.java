package dev.drimoz.contiguousstorage;

import dev.drimoz.contiguousstorage.block.CSBlocks;
import dev.drimoz.contiguousstorage.block.TrimPack;
import dev.drimoz.contiguousstorage.gametest.CSGameTestRegistry;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ContiguousStorage.MOD_ID)
public final class ContiguousStorage {

    public static final String MOD_ID = "contiguousstorage";

    public ContiguousStorage(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CSConfig.SPEC);
        // Lowest: a Functional Storage addon's wood, and its planks, are registered by then.
        modBus.addListener(EventPriority.LOWEST, CSBlocks::register);
        modBus.addListener(TrimPack::addPacks);
        modBus.addListener(CSBlocks::fillCreativeTabs);
        CSGameTestRegistry.register(modBus);
    }
}

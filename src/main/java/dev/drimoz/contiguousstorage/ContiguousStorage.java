package dev.drimoz.contiguousstorage;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ContiguousStorage.MOD_ID)
public final class ContiguousStorage {

    public static final String MOD_ID = "contiguousstorage";

    public ContiguousStorage(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CSConfig.SPEC);
    }
}

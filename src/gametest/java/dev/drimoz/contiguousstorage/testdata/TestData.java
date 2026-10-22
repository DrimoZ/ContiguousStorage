package dev.drimoz.contiguousstorage.testdata;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Dev-only. The scenario driver runs only in a client started with -Pscenario, created once the
 * client exists. The logo and store image tools are on the 1.21.1 branch: what they make does not
 * depend on the game version.
 */
@Mod("contiguousstorage_testdata")
public final class TestData {

    public TestData() {
        if (FMLEnvironment.dist == Dist.CLIENT && Boolean.getBoolean("contiguousstorage.scenario")) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent event) ->
                    MinecraftForge.EVENT_BUS.register(new ClientScenario()));
        }
    }
}

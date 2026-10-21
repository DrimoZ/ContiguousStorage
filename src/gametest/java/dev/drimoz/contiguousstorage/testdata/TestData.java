package dev.drimoz.contiguousstorage.testdata;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/** Dev-only. The scenario driver runs only in a client started with -Pscenario. */
@Mod("contiguousstorage_testdata")
public final class TestData {

    public TestData() {
        if (FMLEnvironment.dist == Dist.CLIENT && Boolean.getBoolean("contiguousstorage.scenario")) {
            NeoForge.EVENT_BUS.register(new ClientScenario());
        }
        if (FMLEnvironment.dist == Dist.CLIENT && Boolean.getBoolean("contiguousstorage.logo")) {
            NeoForge.EVENT_BUS.register(new LogoRender());
        }
    }
}

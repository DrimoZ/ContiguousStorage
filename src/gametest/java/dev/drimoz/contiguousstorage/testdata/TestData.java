package dev.drimoz.contiguousstorage.testdata;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only. The scenario driver runs only in a client started with -Pscenario. The logo and store
 * image tools are on the 1.21.1 branch: what they make does not depend on the game version.
 */
@Mod("contiguousstorage_testdata")
public final class TestData {

    public TestData() {
        if (FMLEnvironment.getDist() == Dist.CLIENT && Boolean.getBoolean("contiguousstorage.scenario")) {
            NeoForge.EVENT_BUS.register(new ClientScenario());
        }
    }
}

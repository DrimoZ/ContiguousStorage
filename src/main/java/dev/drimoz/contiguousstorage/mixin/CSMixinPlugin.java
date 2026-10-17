package dev.drimoz.contiguousstorage.mixin;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the backport only where Functional Storage still has the bug: 1.5.7 and older. FS
 * versions read "1.21.1-1.5.7"; the part after the last dash is FS's own version.
 */
public final class CSMixinPlugin implements IMixinConfigPlugin {

    /** The first FS release expected to carry its own fix (branch 1.21, unreleased on 17 October 2026). */
    private static final DefaultArtifactVersion FIXED_IN = new DefaultArtifactVersion("1.5.8");

    private static final Logger LOGGER = LoggerFactory.getLogger("contiguousstorage");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        ModFileInfo fs = LoadingModList.get().getModFileById("functionalstorage");
        if (fs == null) {
            return false;
        }
        String full = fs.getMods().get(0).getVersion().toString();
        DefaultArtifactVersion version = new DefaultArtifactVersion(full.substring(full.lastIndexOf('-') + 1));
        boolean apply = version.compareTo(FIXED_IN) < 0;
        LOGGER.info("Functional Storage {}: {} the 1.5.7 chunk-load deadlock backport", full, apply ? "applying" : "not applying");
        return apply;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

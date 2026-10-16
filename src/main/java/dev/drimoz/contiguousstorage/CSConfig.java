package dev.drimoz.contiguousstorage;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code contiguousstorage-common.toml}. A world setting, not a player one (CLAUDE.md §6). */
public final class CSConfig {

    public enum Mode {
        /** Functional Storage as shipped. */
        OFF,
        /** Drawers link by touching; the Linking Tool still links at a distance. Never unlinks. */
        ASSIST,
        /** Only drawers that touch the network stay linked. */
        STRICT
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Mode> MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MODE = builder
                .comment("OFF: Functional Storage as shipped.",
                        "ASSIST: a drawer placed against a controller, or against a drawer linked to one, is linked to it.",
                        "        The Linking Tool still links at a distance. Nothing is ever unlinked.",
                        "STRICT: as ASSIST, and a drawer that no longer touches its controller's network is unlinked.",
                        "        WARNING: switching an existing world to STRICT unlinks every drawer that was linked at a",
                        "        distance with the Linking Tool. It is the only setting that undoes a player's work.")
                .defineEnum("mode", Mode.ASSIST);
        SPEC = builder.build();
    }

    private CSConfig() {
    }

    public static Mode mode() {
        return MODE.get();
    }
}

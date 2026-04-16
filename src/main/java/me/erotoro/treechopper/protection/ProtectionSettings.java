package me.erotoro.treechopper.protection;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public record ProtectionSettings(
        boolean enabled,
        boolean checkBreaks,
        boolean checkPlacement,
        Mode mode,
        boolean useWorldGuard,
        boolean useGriefPrevention,
        boolean debug
) {

    public enum Mode {
        FAIL_WHOLE_TREE;

        public static Mode parse(String raw, Mode fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }

            String normalized = raw.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');

            try {
                return Mode.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    public static final ProtectionSettings DEFAULT = new ProtectionSettings(
            true,
            true,
            true,
            Mode.FAIL_WHOLE_TREE,
            true,
            true,
            false
    );

    public static ProtectionSettings load(FileConfiguration config) {
        return new ProtectionSettings(
                config.getBoolean("protection.enabled", DEFAULT.enabled),
                config.getBoolean("protection.check-breaks", DEFAULT.checkBreaks),
                config.getBoolean("protection.check-placement", DEFAULT.checkPlacement),
                Mode.parse(config.getString("protection.mode"), DEFAULT.mode),
                config.getBoolean("protection.use-worldguard", DEFAULT.useWorldGuard),
                config.getBoolean("protection.use-griefprevention", DEFAULT.useGriefPrevention),
                config.getBoolean("protection.debug", DEFAULT.debug)
        );
    }
}

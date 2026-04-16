package me.erotoro.treechopper.replant;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record AutoReplantSettings(
        boolean enabled,
        boolean requireSapling,
        boolean consumeSapling,
        long delayTicksAfterFell,
        boolean replantMegaTrees,
        MegaMode megaMode,
        boolean respectProtection,
        boolean onlyNaturalTrees,
        Set<String> disabledWorlds,
        boolean debug
) {
    public enum MegaMode {
        SINGLE,
        FOUR_SAPLINGS;

        public static MegaMode parse(String raw, MegaMode fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }

            String normalized = raw.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');

            try {
                return MegaMode.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    public static final AutoReplantSettings DEFAULT = new AutoReplantSettings(
            false,
            true,
            true,
            2L,
            false,
            MegaMode.SINGLE,
            true,
            true,
            Set.of(),
            false
    );

    public static AutoReplantSettings load(FileConfiguration config) {
        return new AutoReplantSettings(
                config.getBoolean("auto-replant.enabled", DEFAULT.enabled),
                config.getBoolean("auto-replant.require-sapling", DEFAULT.requireSapling),
                config.getBoolean("auto-replant.consume-sapling", DEFAULT.consumeSapling),
                Math.max(0L, config.getLong("auto-replant.delay-ticks-after-fell", DEFAULT.delayTicksAfterFell)),
                config.getBoolean("auto-replant.replant-mega-trees", DEFAULT.replantMegaTrees),
                MegaMode.parse(config.getString("auto-replant.mega-mode"), DEFAULT.megaMode),
                config.getBoolean("auto-replant.respect-protection", DEFAULT.respectProtection),
                config.getBoolean("auto-replant.only-natural-trees", DEFAULT.onlyNaturalTrees),
                loadDisabledWorlds(config),
                config.getBoolean("auto-replant.debug", DEFAULT.debug)
        );
    }

    public boolean isWorldDisabled(String worldName) {
        return disabledWorlds.contains(normalizeWorldName(worldName));
    }

    private static Set<String> loadDisabledWorlds(FileConfiguration config) {
        Set<String> worlds = new LinkedHashSet<>();
        for (String world : config.getStringList("auto-replant.disabled-worlds")) {
            if (world != null && !world.isBlank()) {
                worlds.add(normalizeWorldName(world));
            }
        }
        return Set.copyOf(worlds);
    }

    private static String normalizeWorldName(String worldName) {
        return worldName == null ? "" : worldName.trim().toLowerCase(Locale.ROOT);
    }
}

package me.erotoro.treechopper.stats;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration for the statistics subsystem (see the {@code stats:} section of config.yml).
 */
public record StatsSettings(
        boolean enabled,
        String storageType,
        String fileName,
        int leaderboardSize,
        int refreshSeconds,
        int flushSeconds
) {
    public static final StatsSettings DEFAULT = new StatsSettings(true, "file", "stats.txt", 10, 60, 30);

    public static StatsSettings load(FileConfiguration config) {
        boolean enabled = config.getBoolean("stats.enabled", DEFAULT.enabled);
        String type = config.getString("stats.storage.type", DEFAULT.storageType);
        String file = config.getString("stats.storage.file", DEFAULT.fileName);
        int size = Math.max(1, config.getInt("stats.leaderboard.size", DEFAULT.leaderboardSize));
        int refresh = Math.max(5, config.getInt("stats.leaderboard.refresh-seconds", DEFAULT.refreshSeconds));
        int flush = Math.max(5, config.getInt("stats.flush-seconds", DEFAULT.flushSeconds));
        String normalizedType = type == null ? DEFAULT.storageType : type.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedFile = (file == null || file.isBlank()) ? DEFAULT.fileName : file.trim();
        return new StatsSettings(enabled, normalizedType, normalizedFile, size, refresh, flush);
    }
}

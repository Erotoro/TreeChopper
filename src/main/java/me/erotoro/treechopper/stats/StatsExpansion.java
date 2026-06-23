package me.erotoro.treechopper.stats;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI expansion exposing TreeChopper statistics. Lives in its own class so it is only
 * loaded/instantiated when PlaceholderAPI is actually present (avoiding {@link NoClassDefFoundError}).
 * All lookups read {@link StatsService}'s in-memory caches and never block.
 *
 * <p>Placeholders:
 * <ul>
 *   <li>{@code %treechopper_trees%} / {@code %treechopper_logs%} / {@code %treechopper_rank%}</li>
 *   <li>{@code %treechopper_top_name_<n>%} / {@code %treechopper_top_trees_<n>%} / {@code %treechopper_top_logs_<n>%}</li>
 * </ul>
 */
public final class StatsExpansion extends PlaceholderExpansion {

    private final StatsService stats;
    private final String version;

    public StatsExpansion(StatsService stats, String version) {
        this.stats = stats;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "treechopper";
    }

    @Override
    public String getAuthor() {
        return "Erotoro";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true; // survive /papi reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }
        String key = params.toLowerCase(Locale.ROOT);

        if (key.startsWith("top_")) {
            return resolveTop(key);
        }

        if (player == null) {
            return null;
        }
        PlayerStats self = stats.getStats(player.getUniqueId(), player.getName());
        switch (key) {
            case "trees":
                return Long.toString(self.treesFelled());
            case "logs":
                return Long.toString(self.logsBroken());
            case "rank":
                int rank = stats.getRank(player.getUniqueId());
                return rank > 0 ? Integer.toString(rank) : "-";
            default:
                return null;
        }
    }

    private String resolveTop(String key) {
        // key forms: top_name_<n> | top_trees_<n> | top_logs_<n>
        int lastUnderscore = key.lastIndexOf('_');
        if (lastUnderscore < 0 || lastUnderscore == key.length() - 1) {
            return null;
        }
        int position;
        try {
            position = Integer.parseInt(key.substring(lastUnderscore + 1));
        } catch (NumberFormatException notANumber) {
            return null;
        }
        if (position < 1) {
            return null;
        }
        String field = key.substring(0, lastUnderscore); // top_name | top_trees | top_logs
        List<PlayerStats> board = stats.getLeaderboard();
        if (position > board.size()) {
            return switch (field) {
                case "top_name" -> "-";
                case "top_trees", "top_logs" -> "0";
                default -> null;
            };
        }
        PlayerStats entry = board.get(position - 1);
        return switch (field) {
            case "top_name" -> entry.name() == null ? "-" : entry.name();
            case "top_trees" -> Long.toString(entry.treesFelled());
            case "top_logs" -> Long.toString(entry.logsBroken());
            default -> null;
        };
    }
}

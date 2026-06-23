package me.erotoro.treechopper.stats;

import java.util.UUID;

/**
 * Immutable snapshot of one player's tree-chopping totals.
 *
 * <p>{@code name} is the last-seen player name, cached purely so leaderboards can be rendered for
 * offline players without a name-resolution round-trip.
 */
public record PlayerStats(UUID uuid, String name, long treesFelled, long logsBroken) {

    public PlayerStats withName(String newName) {
        return new PlayerStats(uuid, newName, treesFelled, logsBroken);
    }

    public PlayerStats add(long treesDelta, long logsDelta) {
        return new PlayerStats(uuid, name, treesFelled + treesDelta, logsBroken + logsDelta);
    }

    public static PlayerStats empty(UUID uuid, String name) {
        return new PlayerStats(uuid, name, 0L, 0L);
    }
}

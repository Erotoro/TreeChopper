package me.erotoro.treechopper.stats;

import java.util.List;
import java.util.UUID;

/**
 * Storage backend for player statistics.
 *
 * <p>All methods are invoked from a single dedicated I/O thread owned by {@link StatsService}, so
 * implementations do not need their own locking for serialization. The default backend is
 * {@link FileStatsRepository} (zero-dependency). A MySQL backend for cross-server networks can be
 * added as a drop-in implementation of this interface without touching the service or expansion.
 */
public interface StatsRepository {

    void init() throws Exception;

    /** Returns the stored stats for the player, or a zeroed record carrying the given name. */
    PlayerStats load(UUID uuid, String name);

    /** Applies an increment for the player, creating the record if necessary. */
    void recordDelta(UUID uuid, String name, long treesDelta, long logsDelta);

    /** Top players ordered by trees felled (descending), at most {@code limit} entries. */
    List<PlayerStats> topByTrees(int limit);

    /** 1-based rank of the player by trees felled, or 0 if they have no recorded trees. */
    int rankByTrees(UUID uuid);

    /** Persists any pending changes. No-op for backends that write through immediately. */
    void flush();

    void close();
}

package me.erotoro.treechopper.stats;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Orchestrates statistics: owns a single background I/O thread for all storage access, keeps
 * non-blocking in-memory caches for online players and the leaderboard (so PlaceholderAPI lookups
 * on the main/region thread never touch disk), and persists periodically.
 *
 * <p>Thread-safety / Folia: the public mutators are safe to call from any region thread — caches are
 * concurrent and the only {@link StatsRepository} access happens on the dedicated I/O thread. No
 * Bukkit API is touched off the main thread.
 */
public final class StatsService {

    private final StatsRepository repository;
    private final StatsSettings settings;
    private final Logger logger;
    private final ScheduledExecutorService io;

    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> rankCache = new ConcurrentHashMap<>();
    private volatile List<PlayerStats> leaderboard = Collections.emptyList();
    private volatile boolean running;

    public StatsService(StatsRepository repository, StatsSettings settings, Logger logger) {
        this.repository = repository;
        this.settings = settings;
        this.logger = logger;
        this.io = Executors.newSingleThreadScheduledExecutor(daemonFactory());
    }

    /** Builds the configured repository (file backend today; MySQL is a future drop-in). */
    public static StatsService create(StatsSettings settings, Path dataFolder, Logger logger) {
        StatsRepository repo;
        if ("mysql".equals(settings.storageType())) {
            logger.warning("[Stats] storage.type 'mysql' is not bundled yet; using the file backend. "
                    + "(The repository interface is ready for a MySQL drop-in.)");
            repo = new FileStatsRepository(dataFolder.resolve(settings.fileName()), logger);
        } else {
            repo = new FileStatsRepository(dataFolder.resolve(settings.fileName()), logger);
        }
        return new StatsService(repo, settings, logger);
    }

    public void start() {
        if (!settings.enabled()) {
            return;
        }
        try {
            repository.init();
        } catch (Exception exception) {
            logger.warning("[Stats] Failed to initialize storage; statistics disabled this session: " + exception.getMessage());
            return;
        }
        running = true;
        io.scheduleWithFixedDelay(this::safeFlush, settings.flushSeconds(), settings.flushSeconds(), TimeUnit.SECONDS);
        io.scheduleWithFixedDelay(this::refreshLeaderboard, 1, settings.refreshSeconds(), TimeUnit.SECONDS);
    }

    public boolean isEnabled() {
        return running;
    }

    /** Record a completed fell for a player: +1 tree and +logs broken. */
    public void recordFell(UUID uuid, String name, long logsBroken) {
        if (!running) {
            return;
        }
        cache.merge(uuid, PlayerStats.empty(uuid, name).add(1, logsBroken),
                (current, add) -> current.withName(name).add(add.treesFelled(), add.logsBroken()));
        io.execute(() -> repository.recordDelta(uuid, name, 1, logsBroken));
    }

    /** Warm the per-player cache when a player joins, so their placeholders resolve instantly. */
    public void onJoin(UUID uuid, String name) {
        if (!running) {
            return;
        }
        io.execute(() -> {
            PlayerStats loaded = repository.load(uuid, name).withName(name);
            cache.put(uuid, loaded);
            rankCache.put(uuid, repository.rankByTrees(uuid));
        });
    }

    public void onQuit(UUID uuid) {
        if (!running) {
            return;
        }
        cache.remove(uuid);
        rankCache.remove(uuid);
    }

    /** Non-blocking: returns cached stats for the player, or a zeroed record. */
    public PlayerStats getStats(UUID uuid, String name) {
        PlayerStats cached = cache.get(uuid);
        return cached != null ? cached : PlayerStats.empty(uuid, name);
    }

    /** Non-blocking: current cached leaderboard. */
    public List<PlayerStats> getLeaderboard() {
        return leaderboard;
    }

    /** Non-blocking: cached rank for the player, 0 if unknown/unranked. */
    public int getRank(UUID uuid) {
        return rankCache.getOrDefault(uuid, 0);
    }

    public void shutdown() {
        running = false;
        io.execute(repository::close);
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void safeFlush() {
        try {
            repository.flush();
        } catch (RuntimeException exception) {
            logger.warning("[Stats] Flush failed: " + exception.getMessage());
        }
    }

    private void refreshLeaderboard() {
        try {
            leaderboard = repository.topByTrees(settings.leaderboardSize());
            for (UUID uuid : cache.keySet()) {
                rankCache.put(uuid, repository.rankByTrees(uuid));
            }
        } catch (RuntimeException exception) {
            logger.warning("[Stats] Leaderboard refresh failed: " + exception.getMessage());
        }
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "TreeChopper-Stats-IO");
            thread.setDaemon(true);
            return thread;
        };
    }
}

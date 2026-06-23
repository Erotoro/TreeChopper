package me.erotoro.treechopper.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsServiceTest {

    private static final Logger LOGGER = Logger.getLogger("test");
    private static final StatsSettings FAST =
            new StatsSettings(true, "file", "stats.txt", 10, 5, 5);

    private static boolean awaitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    @Test
    void cacheReflectsRecordImmediatelyAndLeaderboardRefreshes(@TempDir Path dir) throws Exception {
        StatsService service = StatsService.create(FAST, dir, LOGGER);
        service.start();
        try {
            UUID id = UUID.randomUUID();
            service.recordFell(id, "Steve", 9);
            // Cache (PlaceholderAPI source) updates synchronously.
            assertEquals(1, service.getStats(id, "Steve").treesFelled());
            assertEquals(9, service.getStats(id, "Steve").logsBroken());

            // Leaderboard + rank are refreshed asynchronously (initial delay ~1s).
            assertTrue(awaitUntil(() -> !service.getLeaderboard().isEmpty(), 4000),
                    "leaderboard should populate");
            List<PlayerStats> board = service.getLeaderboard();
            assertEquals("Steve", board.get(0).name());
            assertTrue(awaitUntil(() -> service.getRank(id) == 1, 4000), "rank should resolve to 1");
        } finally {
            service.shutdown();
        }
    }

    @Test
    void shutdownPersistsAndReloads(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        StatsService service = StatsService.create(FAST, dir, LOGGER);
        service.start();
        service.recordFell(id, "Bob", 4);
        service.recordFell(id, "Bob", 6);
        service.shutdown(); // flushes pending writes

        // Verify persisted content via a fresh repository.
        FileStatsRepository repo = new FileStatsRepository(dir.resolve("stats.txt"), LOGGER);
        repo.init();
        PlayerStats persisted = repo.load(id, "Bob");
        assertEquals(2, persisted.treesFelled());
        assertEquals(10, persisted.logsBroken());
    }

    @Test
    void disabledServiceIgnoresRecords() throws Exception {
        StatsSettings disabled = new StatsSettings(false, "file", "stats.txt", 10, 5, 5);
        StatsService service = StatsService.create(disabled, Path.of(System.getProperty("java.io.tmpdir")), LOGGER);
        service.start();
        assertEquals(false, service.isEnabled());
        UUID id = UUID.randomUUID();
        service.recordFell(id, "Nobody", 5);
        assertEquals(0, service.getStats(id, "Nobody").treesFelled());
        service.shutdown();
    }
}

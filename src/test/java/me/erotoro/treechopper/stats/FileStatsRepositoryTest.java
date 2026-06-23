package me.erotoro.treechopper.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStatsRepositoryTest {

    private static final Logger LOGGER = Logger.getLogger("test");

    private FileStatsRepository newRepo(Path dir) throws Exception {
        FileStatsRepository repo = new FileStatsRepository(dir.resolve("stats.txt"), LOGGER);
        repo.init();
        return repo;
    }

    @Test
    void accumulatesDeltasAndPersistsAcrossReload(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        FileStatsRepository repo = newRepo(dir);
        repo.recordDelta(id, "Steve", 1, 7);
        repo.recordDelta(id, "Steve", 1, 5);
        assertEquals(2, repo.load(id, "Steve").treesFelled());
        assertEquals(12, repo.load(id, "Steve").logsBroken());
        repo.flush();
        repo.close();

        FileStatsRepository reloaded = newRepo(dir);
        PlayerStats persisted = reloaded.load(id, "Steve");
        assertEquals(2, persisted.treesFelled());
        assertEquals(12, persisted.logsBroken());
        assertEquals("Steve", persisted.name());
    }

    @Test
    void leaderboardOrdersByTreesDescending(@TempDir Path dir) throws Exception {
        FileStatsRepository repo = newRepo(dir);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        repo.recordDelta(a, "Alice", 5, 50);
        repo.recordDelta(b, "Bob", 10, 20);
        repo.recordDelta(c, "Carol", 1, 99);

        List<PlayerStats> top = repo.topByTrees(2);
        assertEquals(2, top.size());
        assertEquals("Bob", top.get(0).name());
        assertEquals("Alice", top.get(1).name());

        assertEquals(1, repo.rankByTrees(b));
        assertEquals(2, repo.rankByTrees(a));
        assertEquals(3, repo.rankByTrees(c));
    }

    @Test
    void rankIsZeroForUnknownOrZeroTreePlayers(@TempDir Path dir) throws Exception {
        FileStatsRepository repo = newRepo(dir);
        UUID unknown = UUID.randomUUID();
        assertEquals(0, repo.rankByTrees(unknown));
    }

    @Test
    void skipsMalformedLinesOnLoad(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stats.txt");
        UUID id = UUID.randomUUID();
        Files.write(file, (
                id + ";Steve;3;30\n"
                        + "garbage-line\n"
                        + "not-a-uuid;X;1;1\n"
                        + "\n").getBytes(StandardCharsets.UTF_8));
        FileStatsRepository repo = newRepo(dir);
        assertEquals(3, repo.load(id, "Steve").treesFelled());
        assertEquals(1, repo.topByTrees(10).size());
    }

    @Test
    void flushWritesAtomicallyAndIsNoOpWhenClean(@TempDir Path dir) throws Exception {
        FileStatsRepository repo = newRepo(dir);
        UUID id = UUID.randomUUID();
        repo.recordDelta(id, "Steve", 1, 1);
        repo.flush();
        Path file = dir.resolve("stats.txt");
        assertTrue(Files.exists(file));
        long sizeAfterFirst = Files.size(file);
        repo.flush(); // clean -> no-op, file unchanged
        assertEquals(sizeAfterFirst, Files.size(file));
        assertTrue(Files.notExists(dir.resolve("stats.txt.tmp")));
    }
}

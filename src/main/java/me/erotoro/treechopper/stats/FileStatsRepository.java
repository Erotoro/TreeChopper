package me.erotoro.treechopper.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Dependency-free statistics store: the full dataset is held in memory (authoritative for a single
 * server) and snapshotted to a compact, line-delimited file. Snapshots are written atomically
 * (temp file + atomic rename) so a crash mid-write can never corrupt the live data.
 *
 * <p>Line format: {@code uuid;name;treesFelled;logsBroken}. Player names are restricted by Minecraft
 * to {@code [A-Za-z0-9_]} so they never collide with the {@code ;} delimiter.
 */
public final class FileStatsRepository implements StatsRepository {

    private final Path file;
    private final Path tempFile;
    private final Logger logger;
    private final Map<UUID, PlayerStats> data = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public FileStatsRepository(Path file, Logger logger) {
        this.file = file;
        this.tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        this.logger = logger;
    }

    @Override
    public void init() throws IOException {
        data.clear();
        if (!Files.exists(file)) {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            return;
        }
        int loaded = 0;
        int skipped = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            PlayerStats parsed = parse(line);
            if (parsed == null) {
                skipped++;
                continue;
            }
            data.put(parsed.uuid(), parsed);
            loaded++;
        }
        logger.info("[Stats] Loaded " + loaded + " player records" + (skipped > 0 ? " (" + skipped + " malformed skipped)" : "") + ".");
    }

    @Override
    public PlayerStats load(UUID uuid, String name) {
        PlayerStats existing = data.get(uuid);
        return existing != null ? existing : PlayerStats.empty(uuid, name);
    }

    @Override
    public void recordDelta(UUID uuid, String name, long treesDelta, long logsDelta) {
        data.merge(uuid, PlayerStats.empty(uuid, name).add(treesDelta, logsDelta),
                (current, add) -> current.withName(name).add(add.treesFelled(), add.logsBroken()));
        dirty.set(true);
    }

    @Override
    public List<PlayerStats> topByTrees(int limit) {
        List<PlayerStats> all = new ArrayList<>(data.values());
        all.sort(Comparator.comparingLong(PlayerStats::treesFelled).reversed()
                .thenComparing(PlayerStats::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    @Override
    public int rankByTrees(UUID uuid) {
        PlayerStats self = data.get(uuid);
        if (self == null || self.treesFelled() <= 0) {
            return 0;
        }
        int rank = 1;
        for (PlayerStats other : data.values()) {
            if (other.treesFelled() > self.treesFelled()) {
                rank++;
            }
        }
        return rank;
    }

    @Override
    public void flush() {
        if (!dirty.compareAndSet(true, false)) {
            return;
        }
        StringBuilder sb = new StringBuilder(data.size() * 64);
        for (PlayerStats s : data.values()) {
            sb.append(s.uuid()).append(';')
                    .append(s.name() == null ? "" : s.name()).append(';')
                    .append(s.treesFelled()).append(';')
                    .append(s.logsBroken()).append('\n');
        }
        try {
            if (tempFile.getParent() != null) {
                Files.createDirectories(tempFile.getParent());
            }
            Files.write(tempFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                // Some filesystems don't support atomic moves; fall back to a plain replace.
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            dirty.set(true); // retry on the next flush
            logger.warning("[Stats] Failed to write stats file: " + exception.getMessage());
        }
    }

    @Override
    public void close() {
        flush();
        data.clear();
    }

    private static PlayerStats parse(String line) {
        String[] parts = line.split(";", -1);
        if (parts.length != 4) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(parts[0]);
            String name = parts[1];
            long trees = Long.parseLong(parts[2]);
            long logs = Long.parseLong(parts[3]);
            return new PlayerStats(uuid, name.isEmpty() ? null : name, trees, logs);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}

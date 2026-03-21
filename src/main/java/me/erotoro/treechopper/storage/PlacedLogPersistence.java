package me.erotoro.treechopper.storage;

import me.erotoro.treechopper.TreeChopperSettings;
import me.erotoro.treechopper.model.BlockKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class PlacedLogPersistence {

    private PlacedLogPersistence() {
    }

    public static int load(File placedLogsFile, Set<BlockKey> playerPlacedLogs, TreeChopperSettings settings, Logger logger) {
        playerPlacedLogs.clear();

        if (placedLogsFile == null || !placedLogsFile.exists()) {
            return 0;
        }
        if (placedLogsFile.length() > settings.maxPlacedLogsFileBytes()) {
            logger.warning("Skipping placed-logs.yml because it exceeds " + settings.maxPlacedLogsFileBytes() + " bytes.");
            return 0;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(placedLogsFile);
        ConfigurationSection section = configuration.getConfigurationSection("placed-logs");
        if (section == null) {
            return 0;
        }

        int loaded = 0;
        int invalidEntries = 0;
        boolean warnedAboutLimit = false;
        for (String worldIdValue : section.getKeys(false)) {
            UUID worldId;
            try {
                worldId = UUID.fromString(worldIdValue);
            } catch (IllegalArgumentException exception) {
                invalidEntries += warnInvalidEntry("Skipping invalid world id in placed-logs.yml: " + worldIdValue, invalidEntries, settings, logger);
                continue;
            }

            for (String serialized : section.getStringList(worldIdValue)) {
                if (loaded >= settings.maxPlacedLogEntries()) {
                    warnedAboutLimit = true;
                    break;
                }
                String[] parts = serialized.split(",", 3);
                if (parts.length != 3) {
                    invalidEntries += warnInvalidEntry("Skipping invalid placed log entry: " + serialized, invalidEntries, settings, logger);
                    continue;
                }

                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    if (playerPlacedLogs.add(new BlockKey(worldId, x, y, z))) {
                        loaded++;
                    }
                } catch (NumberFormatException exception) {
                    invalidEntries += warnInvalidEntry("Skipping invalid placed log coordinates: " + serialized, invalidEntries, settings, logger);
                }
            }
            if (warnedAboutLimit) {
                break;
            }
        }

        if (warnedAboutLimit) {
            logger.warning("Stopped loading placed logs after reaching the limit of " + settings.maxPlacedLogEntries() + " entries.");
        }
        if (invalidEntries > settings.maxInvalidPlacedLogWarnings()) {
            logger.warning("Suppressed " + (invalidEntries - settings.maxInvalidPlacedLogWarnings()) + " additional invalid placed-log warnings.");
        }

        return loaded;
    }

    public static void save(File placedLogsFile, Set<BlockKey> playerPlacedLogs) throws IOException {
        Map<UUID, List<BlockKey>> byWorld = new HashMap<>();
        for (BlockKey blockKey : playerPlacedLogs) {
            byWorld.computeIfAbsent(blockKey.worldId(), ignored -> new ArrayList<>()).add(blockKey);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<UUID, List<BlockKey>> entry : byWorld.entrySet()) {
            List<String> serialized = new ArrayList<>(entry.getValue().size());
            for (BlockKey blockKey : entry.getValue()) {
                serialized.add(blockKey.x() + "," + blockKey.y() + "," + blockKey.z());
            }
            serialized.sort(String::compareTo);
            configuration.set("placed-logs." + entry.getKey(), serialized);
        }

        saveAtomically(placedLogsFile, configuration);
    }

    private static int warnInvalidEntry(String message, int invalidEntries, TreeChopperSettings settings, Logger logger) {
        if (invalidEntries < settings.maxInvalidPlacedLogWarnings()) {
            logger.warning(message);
        }
        return 1;
    }

    private static void saveAtomically(File placedLogsFile, YamlConfiguration configuration) throws IOException {
        Path target = placedLogsFile.toPath();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        configuration.save(temp.toFile());

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

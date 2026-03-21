package me.erotoro.treechopper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeChopperSettingsTest {

    @Test
    void loadsConfiguredValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("limits.max-logs", 600);
        config.set("limits.leaf-search-radius", 7);
        config.set("limits.foreign-log-scan-radius", 8);
        config.set("performance.max-blocks-per-task", 16);
        config.set("detection.min-leaf-contacts", 4);
        config.set("detection.min-mega-leaf-contacts", 7);
        config.set("detection.max-structure-contacts", 2);
        config.set("storage.max-placed-logs-file-bytes", 8192L);
        config.set("storage.max-placed-log-entries", 9000);
        config.set("storage.max-invalid-placed-log-warnings", 5);

        TreeChopperSettings settings = TreeChopperSettings.load(config);

        assertEquals(600, settings.maxLogs());
        assertEquals(7, settings.leafSearchRadius());
        assertEquals(8, settings.foreignLogScanRadius());
        assertEquals(16, settings.maxBlocksPerTask());
        assertEquals(4, settings.minLeafContacts());
        assertEquals(7, settings.minMegaLeafContacts());
        assertEquals(2, settings.maxStructureContacts());
        assertEquals(8192L, settings.maxPlacedLogsFileBytes());
        assertEquals(9000, settings.maxPlacedLogEntries());
        assertEquals(5, settings.maxInvalidPlacedLogWarnings());
    }

    @Test
    void clampsInvalidValuesToPositiveMinimums() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("limits.max-logs", 0);
        config.set("limits.leaf-search-radius", -1);
        config.set("limits.foreign-log-scan-radius", 0);
        config.set("performance.max-blocks-per-task", -2);
        config.set("detection.min-leaf-contacts", 0);
        config.set("detection.min-mega-leaf-contacts", -10);
        config.set("detection.max-structure-contacts", 0);
        config.set("storage.max-placed-logs-file-bytes", 0L);
        config.set("storage.max-placed-log-entries", -5);
        config.set("storage.max-invalid-placed-log-warnings", 0);

        TreeChopperSettings settings = TreeChopperSettings.load(config);

        assertEquals(1, settings.maxLogs());
        assertEquals(1, settings.leafSearchRadius());
        assertEquals(1, settings.foreignLogScanRadius());
        assertEquals(1, settings.maxBlocksPerTask());
        assertEquals(1, settings.minLeafContacts());
        assertEquals(1, settings.minMegaLeafContacts());
        assertEquals(1, settings.maxStructureContacts());
        assertEquals(1L, settings.maxPlacedLogsFileBytes());
        assertEquals(1, settings.maxPlacedLogEntries());
        assertEquals(1, settings.maxInvalidPlacedLogWarnings());
    }
}

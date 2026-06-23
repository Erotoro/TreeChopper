package me.erotoro.treechopper.stats;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsSettingsTest {

    @Test
    void usesDefaultsForEmptyConfig() {
        StatsSettings s = StatsSettings.load(new YamlConfiguration());
        assertTrue(s.enabled());
        assertEquals("file", s.storageType());
        assertEquals("stats.txt", s.fileName());
        assertEquals(10, s.leaderboardSize());
        assertEquals(60, s.refreshSeconds());
        assertEquals(30, s.flushSeconds());
    }

    @Test
    void clampsAndNormalizes() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("stats.storage.type", "MySQL");
        config.set("stats.leaderboard.size", 0);
        config.set("stats.leaderboard.refresh-seconds", 1);
        config.set("stats.flush-seconds", 1);
        config.set("stats.storage.file", "  ");
        StatsSettings s = StatsSettings.load(config);
        assertEquals("mysql", s.storageType());
        assertEquals(1, s.leaderboardSize());      // min 1
        assertEquals(5, s.refreshSeconds());        // min 5
        assertEquals(5, s.flushSeconds());          // min 5
        assertEquals("stats.txt", s.fileName());    // blank -> default
    }
}

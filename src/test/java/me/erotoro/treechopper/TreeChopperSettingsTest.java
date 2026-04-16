package me.erotoro.treechopper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TreeChopperSettingsTest {

    @Test
    void usesDefaultsWhenConfigSectionIsMissing() {
        TreeChopperSettings settings = TreeChopperSettings.load(new YamlConfiguration());

        assertEquals(TreeChopperSettings.DEFAULT.maxLogs(), settings.maxLogs());
        assertEquals(TreeChopperSettings.DEFAULT.leafSearchRadius(), settings.leafSearchRadius());
        assertEquals(TreeChopperSettings.DEFAULT.foreignLogScanRadius(), settings.foreignLogScanRadius());
        assertEquals(TreeChopperSettings.DEFAULT.activationMode(), settings.activationMode());
        assertEquals(TreeChopperSettings.DEFAULT.autoReplant(), settings.autoReplant());
        assertEquals(TreeChopperSettings.DEFAULT.protection(), settings.protection());
        assertEquals(TreeChopperSettings.DEFAULT.coreProtect(), settings.coreProtect());
        assertEquals(TreeChopperSettings.DEFAULT.playerToggle(), settings.playerToggle());
    }

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
        config.set("activation.mode", "sneak_enable");
        config.set("auto-replant.enabled", true);
        config.set("auto-replant.require-sapling", false);
        config.set("auto-replant.consume-sapling", false);
        config.set("auto-replant.delay-ticks-after-fell", 20L);
        config.set("auto-replant.replant-mega-trees", true);
        config.set("auto-replant.mega-mode", "four-saplings");
        config.set("auto-replant.respect-protection", false);
        config.set("auto-replant.only-natural-trees", false);
        config.set("auto-replant.disabled-worlds", java.util.List.of("world", "world_nether"));
        config.set("auto-replant.debug", true);
        config.set("protection.enabled", false);
        config.set("protection.check-breaks", false);
        config.set("protection.check-placement", false);
        config.set("protection.mode", "fail whole tree");
        config.set("protection.use-worldguard", false);
        config.set("protection.use-griefprevention", false);
        config.set("protection.debug", true);
        config.set("integrations.coreprotect.enabled", false);
        config.set("integrations.coreprotect.debug", true);
        config.set("player-toggle.enabled", false);
        config.set("player-toggle.default-enabled", false);
        config.set("player-toggle.save-on-change", false);

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
        assertEquals(ActivationMode.SNEAK_ENABLE, settings.activationMode());
        assertTrue(settings.autoReplant().enabled());
        assertFalse(settings.autoReplant().requireSapling());
        assertFalse(settings.autoReplant().consumeSapling());
        assertEquals(20L, settings.autoReplant().delayTicksAfterFell());
        assertTrue(settings.autoReplant().replantMegaTrees());
        assertEquals(me.erotoro.treechopper.replant.AutoReplantSettings.MegaMode.FOUR_SAPLINGS, settings.autoReplant().megaMode());
        assertFalse(settings.autoReplant().respectProtection());
        assertFalse(settings.autoReplant().onlyNaturalTrees());
        assertTrue(settings.autoReplant().isWorldDisabled("WORLD"));
        assertTrue(settings.autoReplant().debug());
        assertFalse(settings.protection().enabled());
        assertFalse(settings.protection().checkBreaks());
        assertFalse(settings.protection().checkPlacement());
        assertEquals(me.erotoro.treechopper.protection.ProtectionSettings.Mode.FAIL_WHOLE_TREE, settings.protection().mode());
        assertFalse(settings.protection().useWorldGuard());
        assertFalse(settings.protection().useGriefPrevention());
        assertTrue(settings.protection().debug());
        assertFalse(settings.coreProtect().enabled());
        assertTrue(settings.coreProtect().debug());
        assertFalse(settings.playerToggle().enabled());
        assertFalse(settings.playerToggle().defaultEnabled());
        assertFalse(settings.playerToggle().saveOnChange());
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
        config.set("activation.mode", "invalid");
        config.set("auto-replant.delay-ticks-after-fell", -10L);
        config.set("auto-replant.mega-mode", "unsupported");
        config.set("protection.mode", "invalid");

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
        assertEquals(TreeChopperSettings.DEFAULT.activationMode(), settings.activationMode());
        assertEquals(0L, settings.autoReplant().delayTicksAfterFell());
        assertEquals(me.erotoro.treechopper.replant.AutoReplantSettings.MegaMode.SINGLE, settings.autoReplant().megaMode());
        assertEquals(me.erotoro.treechopper.protection.ProtectionSettings.Mode.FAIL_WHOLE_TREE, settings.protection().mode());
        assertEquals(TreeChopperSettings.DEFAULT.playerToggle(), settings.playerToggle());
    }

    @Test
    void supportsHumanReadableMegaModeValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("auto-replant.mega-mode", "single");

        TreeChopperSettings settings = TreeChopperSettings.load(config);

        assertEquals(me.erotoro.treechopper.replant.AutoReplantSettings.MegaMode.SINGLE, settings.autoReplant().megaMode());
    }

    @Test
    void alwaysOnActivationModeAlwaysActivates() {
        Player player = mock(Player.class);
        when(player.isSneaking()).thenReturn(false);

        TreeChopperSettings settings = new TreeChopperSettings(
                TreeChopperSettings.DEFAULT.maxLogs(),
                TreeChopperSettings.DEFAULT.leafSearchRadius(),
                TreeChopperSettings.DEFAULT.foreignLogScanRadius(),
                TreeChopperSettings.DEFAULT.maxBlocksPerTask(),
                TreeChopperSettings.DEFAULT.minLeafContacts(),
                TreeChopperSettings.DEFAULT.minMegaLeafContacts(),
                TreeChopperSettings.DEFAULT.maxStructureContacts(),
                TreeChopperSettings.DEFAULT.maxPlacedLogsFileBytes(),
                TreeChopperSettings.DEFAULT.maxPlacedLogEntries(),
                TreeChopperSettings.DEFAULT.maxInvalidPlacedLogWarnings(),
                ActivationMode.ALWAYS_ON,
                TreeChopperSettings.DEFAULT.autoReplant(),
                TreeChopperSettings.DEFAULT.protection(),
                TreeChopperSettings.DEFAULT.coreProtect(),
                TreeChopperSettings.DEFAULT.playerToggle()
        );

        assertTrue(settings.shouldActivateFor(player));
    }

    @Test
    void sneakDisableActivationModeBlocksWhileSneaking() {
        Player sneakingPlayer = mock(Player.class);
        when(sneakingPlayer.isSneaking()).thenReturn(true);

        TreeChopperSettings settings = new TreeChopperSettings(
                TreeChopperSettings.DEFAULT.maxLogs(),
                TreeChopperSettings.DEFAULT.leafSearchRadius(),
                TreeChopperSettings.DEFAULT.foreignLogScanRadius(),
                TreeChopperSettings.DEFAULT.maxBlocksPerTask(),
                TreeChopperSettings.DEFAULT.minLeafContacts(),
                TreeChopperSettings.DEFAULT.minMegaLeafContacts(),
                TreeChopperSettings.DEFAULT.maxStructureContacts(),
                TreeChopperSettings.DEFAULT.maxPlacedLogsFileBytes(),
                TreeChopperSettings.DEFAULT.maxPlacedLogEntries(),
                TreeChopperSettings.DEFAULT.maxInvalidPlacedLogWarnings(),
                ActivationMode.SNEAK_DISABLE,
                TreeChopperSettings.DEFAULT.autoReplant(),
                TreeChopperSettings.DEFAULT.protection(),
                TreeChopperSettings.DEFAULT.coreProtect(),
                TreeChopperSettings.DEFAULT.playerToggle()
        );

        assertFalse(settings.shouldActivateFor(sneakingPlayer));
    }

    @Test
    void sneakEnableActivationModeRequiresSneaking() {
        Player standingPlayer = mock(Player.class);
        when(standingPlayer.isSneaking()).thenReturn(false);
        Player sneakingPlayer = mock(Player.class);
        when(sneakingPlayer.isSneaking()).thenReturn(true);

        TreeChopperSettings settings = new TreeChopperSettings(
                TreeChopperSettings.DEFAULT.maxLogs(),
                TreeChopperSettings.DEFAULT.leafSearchRadius(),
                TreeChopperSettings.DEFAULT.foreignLogScanRadius(),
                TreeChopperSettings.DEFAULT.maxBlocksPerTask(),
                TreeChopperSettings.DEFAULT.minLeafContacts(),
                TreeChopperSettings.DEFAULT.minMegaLeafContacts(),
                TreeChopperSettings.DEFAULT.maxStructureContacts(),
                TreeChopperSettings.DEFAULT.maxPlacedLogsFileBytes(),
                TreeChopperSettings.DEFAULT.maxPlacedLogEntries(),
                TreeChopperSettings.DEFAULT.maxInvalidPlacedLogWarnings(),
                ActivationMode.SNEAK_ENABLE,
                TreeChopperSettings.DEFAULT.autoReplant(),
                TreeChopperSettings.DEFAULT.protection(),
                TreeChopperSettings.DEFAULT.coreProtect(),
                TreeChopperSettings.DEFAULT.playerToggle()
        );

        assertFalse(settings.shouldActivateFor(standingPlayer));
        assertTrue(settings.shouldActivateFor(sneakingPlayer));
    }
}

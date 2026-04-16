package me.erotoro.treechopper;

import me.erotoro.treechopper.coreprotect.CoreProtectIntegrationSettings;
import me.erotoro.treechopper.replant.AutoReplantSettings;
import me.erotoro.treechopper.protection.ProtectionSettings;
import me.erotoro.treechopper.toggle.PlayerToggleSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public record TreeChopperSettings(
        int maxLogs,
        int leafSearchRadius,
        int foreignLogScanRadius,
        int maxBlocksPerTask,
        int minLeafContacts,
        int minMegaLeafContacts,
        int maxStructureContacts,
        long maxPlacedLogsFileBytes,
        int maxPlacedLogEntries,
        int maxInvalidPlacedLogWarnings,
        ActivationMode activationMode,
        AutoReplantSettings autoReplant,
        ProtectionSettings protection,
        CoreProtectIntegrationSettings coreProtect,
        PlayerToggleSettings playerToggle
) {
    public static final TreeChopperSettings DEFAULT = new TreeChopperSettings(
            512,
            6,
            6,
            12,
            3,
            6,
            3,
            4L * 1024L * 1024L,
            200_000,
            10,
            ActivationMode.SNEAK_DISABLE,
            AutoReplantSettings.DEFAULT,
            ProtectionSettings.DEFAULT,
            CoreProtectIntegrationSettings.DEFAULT,
            PlayerToggleSettings.DEFAULT
    );

    public static TreeChopperSettings load(FileConfiguration config) {
        return new TreeChopperSettings(
                positiveInt(config, "limits.max-logs", DEFAULT.maxLogs),
                positiveInt(config, "limits.leaf-search-radius", DEFAULT.leafSearchRadius),
                positiveInt(config, "limits.foreign-log-scan-radius", DEFAULT.foreignLogScanRadius),
                positiveInt(config, "performance.max-blocks-per-task", DEFAULT.maxBlocksPerTask),
                positiveInt(config, "detection.min-leaf-contacts", DEFAULT.minLeafContacts),
                positiveInt(config, "detection.min-mega-leaf-contacts", DEFAULT.minMegaLeafContacts),
                positiveInt(config, "detection.max-structure-contacts", DEFAULT.maxStructureContacts),
                positiveLong(config, "storage.max-placed-logs-file-bytes", DEFAULT.maxPlacedLogsFileBytes),
                positiveInt(config, "storage.max-placed-log-entries", DEFAULT.maxPlacedLogEntries),
                positiveInt(config, "storage.max-invalid-placed-log-warnings", DEFAULT.maxInvalidPlacedLogWarnings),
                ActivationMode.parse(config.getString("activation.mode"), DEFAULT.activationMode),
                AutoReplantSettings.load(config),
                ProtectionSettings.load(config),
                CoreProtectIntegrationSettings.load(config),
                PlayerToggleSettings.load(config)
        );
    }

    public boolean shouldActivateFor(Player player) {
        return switch (activationMode) {
            case ALWAYS_ON -> true;
            case SNEAK_DISABLE -> !player.isSneaking();
            case SNEAK_ENABLE -> player.isSneaking();
        };
    }

    private static int positiveInt(FileConfiguration config, String path, int fallback) {
        return Math.max(1, config.getInt(path, fallback));
    }

    private static long positiveLong(FileConfiguration config, String path, long fallback) {
        return Math.max(1L, config.getLong(path, fallback));
    }
}

package me.erotoro.treechopper.coreprotect;

import org.bukkit.configuration.file.FileConfiguration;

public record CoreProtectIntegrationSettings(
        boolean enabled,
        boolean debug
) {
    public static final CoreProtectIntegrationSettings DEFAULT = new CoreProtectIntegrationSettings(true, false);

    public static CoreProtectIntegrationSettings load(FileConfiguration config) {
        return new CoreProtectIntegrationSettings(
                config.getBoolean("integrations.coreprotect.enabled", DEFAULT.enabled),
                config.getBoolean("integrations.coreprotect.debug", DEFAULT.debug)
        );
    }
}

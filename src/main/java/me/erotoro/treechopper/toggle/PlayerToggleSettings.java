package me.erotoro.treechopper.toggle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record PlayerToggleSettings(
        boolean enabled,
        boolean defaultEnabled,
        boolean saveOnChange
) {
    public static final PlayerToggleSettings DEFAULT = new PlayerToggleSettings(true, true, true);

    public static PlayerToggleSettings load(FileConfiguration configuration) {
        if (configuration == null) {
            return DEFAULT;
        }

        ConfigurationSection section = configuration.getConfigurationSection("player-toggle");
        if (section == null) {
            return DEFAULT;
        }

        return new PlayerToggleSettings(
                section.getBoolean("enabled", DEFAULT.enabled),
                section.getBoolean("default-enabled", DEFAULT.defaultEnabled),
                section.getBoolean("save-on-change", DEFAULT.saveOnChange)
        );
    }
}

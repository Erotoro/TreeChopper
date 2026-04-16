package me.erotoro.treechopper.coreprotect;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class CoreProtectProvider {

    private static final String COREPROTECT_PLUGIN_NAME = "CoreProtect";
    private static final int MIN_API_VERSION = 10;

    private final PluginManager pluginManager;
    private final Logger logger;
    private final boolean debug;

    public CoreProtectProvider(PluginManager pluginManager, JavaPlugin plugin, boolean debug) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.logger = Objects.requireNonNull(plugin, "plugin").getLogger();
        this.debug = debug;
    }

    public Optional<CoreProtectAPI> resolve() {
        Plugin plugin = pluginManager.getPlugin(COREPROTECT_PLUGIN_NAME);
        if (!(plugin instanceof CoreProtect coreProtectPlugin)) {
            debug("CoreProtect plugin is not installed or not compatible with API access.");
            return Optional.empty();
        }

        CoreProtectAPI api = coreProtectPlugin.getAPI();
        if (api == null) {
            debug("CoreProtect API instance is null.");
            return Optional.empty();
        }
        if (!api.isEnabled()) {
            debug("CoreProtect API is disabled.");
            return Optional.empty();
        }
        if (api.APIVersion() < MIN_API_VERSION) {
            debug("CoreProtect API version is incompatible. Required >= " + MIN_API_VERSION + ", got " + api.APIVersion());
            return Optional.empty();
        }
        return Optional.of(api);
    }

    private void debug(String message) {
        if (debug) {
            logger.info("[CoreProtect] " + message);
        }
    }
}

package me.erotoro.treechopper.coreprotect;

import net.coreprotect.CoreProtectAPI;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class CoreProtectService {

    private final Optional<CoreProtectAPI> api;
    private final Logger logger;
    private final boolean debug;

    private CoreProtectService(Optional<CoreProtectAPI> api, Logger logger, boolean debug) {
        this.api = api;
        this.logger = logger;
        this.debug = debug;
    }

    public static CoreProtectService initialize(JavaPlugin plugin, PluginManager pluginManager,
                                                CoreProtectIntegrationSettings settings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(pluginManager, "pluginManager");
        Objects.requireNonNull(settings, "settings");

        if (!settings.enabled()) {
            if (settings.debug()) {
                plugin.getLogger().info("[CoreProtect] Integration disabled by config.");
            }
            return new CoreProtectService(Optional.empty(), plugin.getLogger(), settings.debug());
        }

        CoreProtectProvider provider = new CoreProtectProvider(pluginManager, plugin, settings.debug());
        Optional<CoreProtectAPI> api = provider.resolve();
        if (api.isEmpty() && settings.debug()) {
            plugin.getLogger().info("[CoreProtect] Integration unavailable. Running without CoreProtect logging.");
        }
        return new CoreProtectService(api, plugin.getLogger(), settings.debug());
    }

    public boolean isAvailable() {
        return api.isPresent();
    }

    public void logRemoval(String actorName, BlockState originalState) {
        if (actorName == null || actorName.isBlank() || originalState == null || originalState.getType().isAir()) {
            return;
        }
        try {
            api.ifPresent(coreProtectAPI -> coreProtectAPI.logRemoval(actorName, originalState));
        } catch (RuntimeException exception) {
            debug("Failed to log removal at "
                    + originalState.getX() + "," + originalState.getY() + "," + originalState.getZ()
                    + ": " + exception.getMessage());
        }
    }

    public void logPlacement(String actorName, BlockState placedState) {
        if (actorName == null || actorName.isBlank() || placedState == null || placedState.getType().isAir()) {
            return;
        }
        try {
            api.ifPresent(coreProtectAPI -> coreProtectAPI.logPlacement(actorName, placedState));
        } catch (RuntimeException exception) {
            debug("Failed to log placement at "
                    + placedState.getX() + "," + placedState.getY() + "," + placedState.getZ()
                    + ": " + exception.getMessage());
        }
    }

    private void debug(String message) {
        if (debug) {
            logger.info("[CoreProtect] " + message);
        }
    }
}

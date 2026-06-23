package me.erotoro.treechopper.coreprotect;

import me.erotoro.treechopper.tree.TreeMaterials;
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
        if (api.isEmpty() || actorName == null || actorName.isBlank()
                || originalState == null || TreeMaterials.isAir(originalState.getType())) {
            return;
        }
        // Deliberately NOT a lambda: api.ifPresent(x -> x.logRemoval(...)) builds the lambda even when
        // the Optional is empty, and that invokedynamic eagerly loads CoreProtectAPI — which then
        // escapes the try/catch once JvmDowngrader desugars it. With the isEmpty() guard above, the
        // CoreProtectAPI-referencing call below only runs when CoreProtect is actually installed.
        try {
            api.get().logRemoval(actorName, originalState);
        } catch (NoClassDefFoundError error) {
            debug("CoreProtect classes are unavailable at runtime; removal logging skipped.");
        } catch (RuntimeException exception) {
            debug("Failed to log removal at "
                    + originalState.getX() + "," + originalState.getY() + "," + originalState.getZ()
                    + ": " + exception.getMessage());
        }
    }

    public void logPlacement(String actorName, BlockState placedState) {
        if (api.isEmpty() || actorName == null || actorName.isBlank()
                || placedState == null || TreeMaterials.isAir(placedState.getType())) {
            return;
        }
        try {
            api.get().logPlacement(actorName, placedState);
        } catch (NoClassDefFoundError error) {
            debug("CoreProtect classes are unavailable at runtime; placement logging skipped.");
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

package me.erotoro.treechopper.protection;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public final class ProtectionService {

    private final ProtectionSettings settings;
    private final List<ProtectionHook> hooks;
    private final Logger logger;

    public ProtectionService(ProtectionSettings settings, List<ProtectionHook> hooks, Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.hooks = List.copyOf(Objects.requireNonNull(hooks, "hooks"));
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static ProtectionService initialize(JavaPlugin plugin, PluginManager pluginManager, ProtectionSettings settings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(pluginManager, "pluginManager");

        Logger logger = plugin.getLogger();
        List<ProtectionHook> hooks = new ArrayList<>();

        if (settings.enabled() && settings.useWorldGuard() && isPluginEnabled(pluginManager, "WorldGuard")) {
            try {
                hooks.add(new WorldGuardHook(pluginManager, settings.debug(), logger));
                logger.info("[Protection] WorldGuard hook enabled.");
            } catch (Throwable throwable) {
                logger.warning("[TreeChopper] Protection hook unavailable: WorldGuard (" + throwable.getMessage() + ")");
            }
        }

        if (settings.enabled() && settings.useGriefPrevention() && isPluginEnabled(pluginManager, "GriefPrevention")) {
            try {
                hooks.add(new GriefPreventionHook(pluginManager, settings.debug(), logger));
                logger.info("[Protection] GriefPrevention hook enabled.");
            } catch (Throwable throwable) {
                logger.warning("[TreeChopper] Protection hook unavailable: GriefPrevention (" + throwable.getMessage() + ")");
            }
        }

        if (hooks.isEmpty()) {
            hooks.add(new NoopProtectionHook());
            logger.info("[Protection] No protection hooks active. Using noop hook.");
        }

        return new ProtectionService(settings, hooks, logger);
    }

    public List<ProtectionHook> hooks() {
        return Collections.unmodifiableList(hooks);
    }

    public boolean canBreak(Player player, Block block) {
        if (!settings.enabled() || !settings.checkBreaks()) {
            return true;
        }
        if (player == null || block == null || block.getWorld() == null) {
            debug("Denied break because player or block is unavailable.");
            return false;
        }

        for (ProtectionHook hook : hooks) {
            boolean allowed;
            try {
                allowed = hook.canBreak(player, block);
            } catch (Throwable throwable) {
                debug("Hook error (break) in " + hook.getName() + " at " + formatBlock(block) + ": " + throwable.getMessage() + " -> treated as non-applicable");
                continue;
            }
            if (!allowed) {
                debug("Protection deny (break) by " + hook.getName() + " at " + formatBlock(block));
                return false;
            }
        }
        return true;
    }

    public boolean canBreakAll(Player player, Collection<Block> blocks) {
        if (!settings.enabled() || !settings.checkBreaks()) {
            return true;
        }
        if (blocks == null || blocks.isEmpty()) {
            debug("Denied break-all because the block collection is empty.");
            return false;
        }

        for (Block block : blocks) {
            if (!canBreak(player, block)) {
                return false;
            }
        }
        return true;
    }

    public boolean canPlace(Player player, Block block, Material material) {
        if (!settings.enabled() || !settings.checkPlacement()) {
            return true;
        }
        if (player == null || block == null || block.getWorld() == null || material == null) {
            debug("Denied place because player, block, or material is unavailable.");
            return false;
        }

        for (ProtectionHook hook : hooks) {
            boolean allowed;
            try {
                allowed = hook.canPlace(player, block, material);
            } catch (Throwable throwable) {
                debug("Hook error (place) in " + hook.getName() + " at " + formatBlock(block) + ": " + throwable.getMessage() + " -> treated as non-applicable");
                continue;
            }
            if (!allowed) {
                debug("Protection deny (place) by " + hook.getName() + " at " + formatBlock(block));
                return false;
            }
        }
        return true;
    }

    private void debug(String message) {
        if (settings.debug()) {
            logger.info("[TreeChopper] " + message);
        }
    }

    private static boolean isPluginEnabled(PluginManager pluginManager, String pluginName) {
        return pluginManager.getPlugin(pluginName) != null && pluginManager.getPlugin(pluginName).isEnabled();
    }

    private static String formatBlock(Block block) {
        return block.getWorld().getName() + "@" + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}

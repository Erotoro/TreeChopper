package me.erotoro.treechopper.protection;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.bukkit.ProtectionQuery;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;
import java.util.logging.Logger;

public final class WorldGuardHook implements ProtectionHook {

    private final WorldGuardPlugin worldGuardPlugin;
    private final RegionContainer regionContainer;
    private final boolean debug;
    private final Logger logger;

    public WorldGuardHook(PluginManager pluginManager, boolean debug, Logger logger) {
        Objects.requireNonNull(pluginManager, "pluginManager");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.debug = debug;

        Plugin plugin = pluginManager.getPlugin("WorldGuard");
        if (!(plugin instanceof WorldGuardPlugin worldGuard) || !plugin.isEnabled()) {
            throw new IllegalStateException("WorldGuard plugin is not enabled.");
        }

        this.worldGuardPlugin = worldGuard;
        this.regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        if (this.regionContainer == null) {
            throw new IllegalStateException("WorldGuard region container is unavailable.");
        }
    }

    @Override
    public String getName() {
        return "WorldGuard";
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        if (!isContextReady(player, block)) {
            return false;
        }
        try {
            ProtectionQuery query = worldGuardPlugin.createProtectionQuery();
            boolean allowed = query.testBlockBreak(player, block);
            logRegionState("break", block, allowed);
            return allowed;
        } catch (Throwable throwable) {
            if (debug) {
                logger.info("[TreeChopper] Protection WorldGuard non-applicable (break) at "
                        + formatBlock(block) + ": " + throwable.getMessage());
            }
            return true;
        }
    }

    @Override
    public boolean canPlace(Player player, Block block, Material material) {
        if (!isContextReady(player, block) || material == null) {
            return false;
        }
        try {
            ProtectionQuery query = worldGuardPlugin.createProtectionQuery();
            boolean allowed = query.testBlockPlace(player, block.getLocation(), material);
            logRegionState("place", block, allowed);
            return allowed;
        } catch (Throwable throwable) {
            if (debug) {
                logger.info("[TreeChopper] Protection WorldGuard non-applicable (place) at "
                        + formatBlock(block) + ": " + throwable.getMessage());
            }
            return true;
        }
    }

    private boolean isContextReady(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        World world = block.getWorld();
        return world != null && world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    private void logRegionState(String action, Block block, boolean allowed) {
        if (!debug || block == null) {
            return;
        }
        try {
            Location location = block.getLocation();
            RegionQuery regionQuery = regionContainer.createQuery();
            ApplicableRegionSet regions = regionQuery.getApplicableRegions(BukkitAdapter.adapt(location));
            if (regions.size() == 0) {
                logger.info("[TreeChopper] Protection allow no-region by WorldGuard (" + action + ") at " + formatBlock(block));
                return;
            }
            if (!allowed) {
                logger.info("[TreeChopper] Protection deny (" + action + ") by WorldGuard at " + formatBlock(block));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String formatBlock(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}

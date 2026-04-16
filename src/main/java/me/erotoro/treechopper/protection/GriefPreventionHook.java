package me.erotoro.treechopper.protection;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Objects;
import java.util.logging.Logger;

public final class GriefPreventionHook implements ProtectionHook {

    private final GriefPrevention griefPrevention;
    private final boolean debug;
    private final Logger logger;

    public GriefPreventionHook(PluginManager pluginManager, boolean debug, Logger logger) {
        Objects.requireNonNull(pluginManager, "pluginManager");
        this.debug = debug;
        this.logger = Objects.requireNonNull(logger, "logger");

        Plugin plugin = pluginManager.getPlugin("GriefPrevention");
        if (!(plugin instanceof GriefPrevention gp) || !plugin.isEnabled()) {
            throw new IllegalStateException("GriefPrevention plugin is not enabled.");
        }

        this.griefPrevention = gp;
        if (this.griefPrevention.dataStore == null) {
            throw new IllegalStateException("GriefPrevention dataStore is unavailable.");
        }
    }

    @Override
    public String getName() {
        return "GriefPrevention";
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        if (!isContextReady(player, block)) {
            return false;
        }

        try {
            Location location = block.getLocation();
            Claim claim = griefPrevention.dataStore.getClaimAt(location, false, null);
            if (claim == null) {
                if (debug) {
                    logger.info("[TreeChopper] Protection allow outside claim by GriefPrevention (break) at " + formatBlock(block));
                }
                return true;
            }
            String denial = griefPrevention.allowBreak(player, block, block.getLocation());
            if (debug && denial != null) {
                logger.info("[TreeChopper] Protection deny (break) by GriefPrevention at " + formatBlock(block));
            }
            return denial == null;
        } catch (Throwable throwable) {
            if (debug) {
                logger.info("[TreeChopper] Protection GriefPrevention non-applicable (break) at "
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
            Location location = block.getLocation();
            Claim claim = griefPrevention.dataStore.getClaimAt(location, false, null);
            if (claim == null) {
                if (debug) {
                    logger.info("[TreeChopper] Protection allow outside claim by GriefPrevention (place) at " + formatBlock(block));
                }
                return true;
            }
            String denial = griefPrevention.allowBuild(player, block.getLocation(), material);
            if (debug && denial != null) {
                logger.info("[TreeChopper] Protection deny (place) by GriefPrevention at " + formatBlock(block));
            }
            return denial == null;
        } catch (Throwable throwable) {
            if (debug) {
                logger.info("[TreeChopper] Protection GriefPrevention non-applicable (place) at "
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
        if (world == null) {
            return false;
        }

        return world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    private static String formatBlock(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}

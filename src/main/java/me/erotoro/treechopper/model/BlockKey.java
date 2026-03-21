package me.erotoro.treechopper.model;

import org.bukkit.block.Block;

import java.util.UUID;

public record BlockKey(UUID worldId, int x, int y, int z) {
    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}

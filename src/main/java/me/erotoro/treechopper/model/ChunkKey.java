package me.erotoro.treechopper.model;

import org.bukkit.block.Block;

import java.util.UUID;

public record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    public static ChunkKey of(Block block) {
        return new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }
}

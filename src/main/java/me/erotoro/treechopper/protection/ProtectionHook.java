package me.erotoro.treechopper.protection;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface ProtectionHook {

    String getName();

    boolean canBreak(Player player, Block block);

    boolean canPlace(Player player, Block block, Material material);
}

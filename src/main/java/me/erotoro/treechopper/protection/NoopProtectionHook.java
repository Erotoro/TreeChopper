package me.erotoro.treechopper.protection;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class NoopProtectionHook implements ProtectionHook {

    @Override
    public String getName() {
        return "Noop";
    }

    @Override
    public boolean canBreak(Player player, Block block) {
        return true;
    }

    @Override
    public boolean canPlace(Player player, Block block, Material material) {
        return true;
    }
}

package me.erotoro.treechopper.model;

import org.bukkit.block.Block;

import java.util.List;

public final class BreakPlan {
    public final List<Block> blocks;
    public final int durabilityDamage;

    public BreakPlan(List<Block> blocks, int durabilityDamage) {
        this.blocks = blocks;
        this.durabilityDamage = durabilityDamage;
    }
}

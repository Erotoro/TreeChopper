package me.erotoro.treechopper.model;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Set;

public final class TreeSearchResult {
    public final Set<Block> treeBlocks;
    public final boolean foundLeaves;
    public final boolean foundPlayerPlaced;
    public final Material logType;

    public TreeSearchResult(Set<Block> treeBlocks, boolean foundLeaves, boolean foundPlayerPlaced, Material logType) {
        this.treeBlocks = treeBlocks;
        this.foundLeaves = foundLeaves;
        this.foundPlayerPlaced = foundPlayerPlaced;
        this.logType = logType;
    }
}

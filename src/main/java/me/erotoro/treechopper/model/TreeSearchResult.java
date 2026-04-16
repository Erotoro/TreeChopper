package me.erotoro.treechopper.model;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Set;

public final class TreeSearchResult {
    public final Set<Block> treeBlocks;
    public final Set<BlockKey> trunkBaseBlocks;
    public final boolean foundLeaves;
    public final boolean foundPlayerPlaced;
    public final boolean megaTree;
    public final Material logType;

    public TreeSearchResult(Set<Block> treeBlocks, Set<BlockKey> trunkBaseBlocks, boolean foundLeaves,
                            boolean foundPlayerPlaced, boolean megaTree, Material logType) {
        this.treeBlocks = treeBlocks;
        this.trunkBaseBlocks = trunkBaseBlocks;
        this.foundLeaves = foundLeaves;
        this.foundPlayerPlaced = foundPlayerPlaced;
        this.megaTree = megaTree;
        this.logType = logType;
    }
}

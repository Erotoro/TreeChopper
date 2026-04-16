package me.erotoro.treechopper.replant;

import me.erotoro.treechopper.model.BlockKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Set;

public record ReplantRequest(
        Player player,
        Location anchor,
        Material logType,
        Set<BlockKey> trunkBaseBlocks,
        boolean megaTree,
        boolean naturalTree
) {
    public ReplantRequest {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(logType, "logType");
        Objects.requireNonNull(trunkBaseBlocks, "trunkBaseBlocks");
        trunkBaseBlocks = Set.copyOf(trunkBaseBlocks);
    }
}

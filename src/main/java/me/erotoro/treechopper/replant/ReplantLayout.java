package me.erotoro.treechopper.replant;

import me.erotoro.treechopper.model.BlockKey;

import java.util.List;

public record ReplantLayout(List<BlockKey> targetBlocks) {
    public int saplingsRequired() {
        return targetBlocks.size();
    }
}

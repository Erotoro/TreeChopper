package me.erotoro.treechopper.replant;

import me.erotoro.treechopper.model.BlockKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class ReplantLayoutPlanner {

    public ReplantLayout plan(Set<BlockKey> trunkBaseBlocks, boolean megaTree, boolean replantMegaTrees,
                              AutoReplantSettings.MegaMode megaMode) {
        if (trunkBaseBlocks.isEmpty()) {
            return new ReplantLayout(List.of());
        }

        List<BlockKey> sorted = trunkBaseBlocks.stream()
                .sorted(Comparator.comparingInt(BlockKey::y).thenComparingInt(BlockKey::x).thenComparingInt(BlockKey::z))
                .toList();

        if (!megaTree) {
            return new ReplantLayout(List.of(sorted.get(0)));
        }
        if (!replantMegaTrees) {
            return new ReplantLayout(List.of());
        }
        if (megaMode == AutoReplantSettings.MegaMode.FOUR_SAPLINGS && sorted.size() == 4) {
            return new ReplantLayout(new ArrayList<>(sorted));
        }
        return new ReplantLayout(List.of(sorted.get(0)));
    }
}

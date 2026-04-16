package me.erotoro.treechopper.replant;

import me.erotoro.treechopper.model.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplantLayoutPlannerTest {

    private final ReplantLayoutPlanner planner = new ReplantLayoutPlanner();

    @Test
    void plansSingleForNormalTrees() {
        Set<BlockKey> trunkBases = Set.of(
                new BlockKey(UUID.randomUUID(), 10, 64, 10)
        );

        ReplantLayout layout = planner.plan(trunkBases, false, false, AutoReplantSettings.MegaMode.SINGLE);

        assertEquals(1, layout.targetBlocks().size());
        assertEquals(1, layout.saplingsRequired());
    }

    @Test
    void skipsMegaWhenMegaReplantIsDisabled() {
        Set<BlockKey> trunkBases = Set.of(
                new BlockKey(UUID.randomUUID(), 10, 64, 10),
                new BlockKey(UUID.randomUUID(), 11, 64, 10),
                new BlockKey(UUID.randomUUID(), 10, 64, 11),
                new BlockKey(UUID.randomUUID(), 11, 64, 11)
        );

        ReplantLayout layout = planner.plan(trunkBases, true, false, AutoReplantSettings.MegaMode.FOUR_SAPLINGS);

        assertTrue(layout.targetBlocks().isEmpty());
    }

    @Test
    void plansFourWhenMegaModeIsFourSaplings() {
        UUID worldId = UUID.randomUUID();
        Set<BlockKey> trunkBases = Set.of(
                new BlockKey(worldId, 10, 64, 10),
                new BlockKey(worldId, 11, 64, 10),
                new BlockKey(worldId, 10, 64, 11),
                new BlockKey(worldId, 11, 64, 11)
        );

        ReplantLayout layout = planner.plan(trunkBases, true, true, AutoReplantSettings.MegaMode.FOUR_SAPLINGS);

        assertEquals(4, layout.targetBlocks().size());
        assertEquals(4, layout.saplingsRequired());
    }

    @Test
    void plansSingleWhenMegaModeIsSingle() {
        UUID worldId = UUID.randomUUID();
        Set<BlockKey> trunkBases = Set.of(
                new BlockKey(worldId, 10, 64, 10),
                new BlockKey(worldId, 11, 64, 10),
                new BlockKey(worldId, 10, 64, 11),
                new BlockKey(worldId, 11, 64, 11)
        );

        ReplantLayout layout = planner.plan(trunkBases, true, true, AutoReplantSettings.MegaMode.SINGLE);

        assertEquals(1, layout.targetBlocks().size());
        assertEquals(1, layout.saplingsRequired());
    }
}

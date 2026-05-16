package me.erotoro.treechopper.tree;

import me.erotoro.treechopper.TreeChopperSettings;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSearchServiceTest {

    private static final int[][] NEIGHBOR_OFFSETS = createNeighborOffsets();
    private static final int[][] CARDINAL_OFFSETS = new int[][]{
            {1, 0, 0},
            {-1, 0, 0},
            {0, 0, 1},
            {0, 0, -1}
    };

    @Test
    void includesGroundedSecondaryCherryTrunkConnectedByNaturalBranch() {
        Material cherryLog = Material.CHERRY_LOG;
        Material cherryLeaves = Material.CHERRY_LEAVES;
        TestBlockWorld world = new TestBlockWorld();

        for (int y = 64; y <= 68; y++) {
            world.setType(0, y, 0, cherryLog);
            world.setType(4, y, 0, cherryLog);
        }
        world.setType(1, 66, 0, cherryLog);
        world.setType(2, 66, 0, cherryLog);
        world.setType(3, 66, 0, cherryLog);

        world.setType(0, 69, 0, cherryLeaves);
        world.setType(1, 69, 0, cherryLeaves);
        world.setType(-1, 69, 0, cherryLeaves);
        world.setType(4, 69, 0, cherryLeaves);
        world.setType(5, 69, 0, cherryLeaves);
        world.setType(3, 69, 0, cherryLeaves);

        TreeSearchService service = new TreeSearchService(Set.of(), TreeChopperSettings.DEFAULT, NEIGHBOR_OFFSETS, CARDINAL_OFFSETS);

        Block start = world.blockAt(0, 64, 0);
        var result = service.findTree(start, 64);

        assertTrue(result.foundLeaves);
        assertFalse(result.foundPlayerPlaced);
        assertTrue(result.treeBlocks.contains(world.blockAt(4, 64, 0)),
                "Natural split cherry trees should include the grounded secondary trunk.");
    }

    @Test
    void includesLowGroundedSupportTrunkForPaleOakWhenAvailable() {
        Material paleOakLog = Material.getMaterial("PALE_OAK_LOG");
        Material paleOakLeaves = Material.getMaterial("PALE_OAK_LEAVES");
        Material logType = paleOakLog != null ? paleOakLog : Material.OAK_LOG;
        Material leafType = paleOakLeaves != null ? paleOakLeaves : Material.OAK_LEAVES;

        TestBlockWorld world = new TestBlockWorld();
        for (int y = 64; y <= 69; y++) {
            world.setType(0, y, 0, logType);
        }
        for (int y = 64; y <= 66; y++) {
            world.setType(3, y, 0, logType);
        }
        world.setType(1, 64, 0, logType);
        world.setType(2, 64, 0, logType);

        world.setType(0, 70, 0, leafType);
        world.setType(1, 70, 0, leafType);
        world.setType(-1, 70, 0, leafType);
        world.setType(3, 67, 0, leafType);
        world.setType(4, 67, 0, leafType);

        TreeSearchService service = new TreeSearchService(Set.of(), TreeChopperSettings.DEFAULT, NEIGHBOR_OFFSETS, CARDINAL_OFFSETS);

        Block start = world.blockAt(0, 64, 0);
        var result = service.findTree(start, 64);

        assertTrue(result.foundLeaves);
        assertFalse(result.foundPlayerPlaced);
        assertTrue(result.treeBlocks.contains(world.blockAt(3, 64, 0)),
                "Low grounded support trunks should be included for pale oak-like split trees.");
    }

    @Test
    void includesCherrySupportColumnWhenBranchStartsLowOnTrunk() {
        Material cherryLog = Material.CHERRY_LOG;
        Material cherryLeaves = Material.CHERRY_LEAVES;
        TestBlockWorld world = new TestBlockWorld();

        for (int y = 64; y <= 71; y++) {
            world.setType(0, y, 0, cherryLog);
        }

        world.setType(1, 65, 0, cherryLog);
        for (int y = 64; y <= 68; y++) {
            world.setType(2, y, 0, cherryLog);
        }

        world.setType(0, 72, 0, cherryLeaves);
        world.setType(1, 72, 0, cherryLeaves);
        world.setType(-1, 72, 0, cherryLeaves);
        world.setType(2, 69, 0, cherryLeaves);
        world.setType(3, 69, 0, cherryLeaves);
        world.setType(2, 68, 1, cherryLeaves);

        TreeSearchService service = new TreeSearchService(Set.of(), TreeChopperSettings.DEFAULT, NEIGHBOR_OFFSETS, CARDINAL_OFFSETS);

        var result = service.findTree(world.blockAt(0, 64, 0), 64);

        assertTrue(result.foundLeaves);
        assertFalse(result.foundPlayerPlaced);
        assertTrue(result.treeBlocks.contains(world.blockAt(2, 64, 0)),
                "Cherry support columns connected by a low elbow should be fully included down to the ground.");
    }

    @Test
    void ignoresLowDeadEndLogsThatDoNotLeadToLeafBearingBranches() {
        Material cherryLog = Material.CHERRY_LOG;
        Material cherryLeaves = Material.CHERRY_LEAVES;
        TestBlockWorld world = new TestBlockWorld();

        for (int y = 64; y <= 70; y++) {
            world.setType(0, y, 0, cherryLog);
        }
        world.setType(-1, 64, 0, cherryLog);
        world.setType(-2, 64, 0, cherryLog);

        world.setType(0, 71, 0, cherryLeaves);
        world.setType(1, 71, 0, cherryLeaves);
        world.setType(-1, 71, 0, cherryLeaves);

        TreeSearchService service = new TreeSearchService(Set.of(), TreeChopperSettings.DEFAULT, NEIGHBOR_OFFSETS, CARDINAL_OFFSETS);

        var result = service.findTree(world.blockAt(0, 64, 0), 64);

        assertTrue(result.foundLeaves);
        assertFalse(result.treeBlocks.contains(world.blockAt(-2, 64, 0)),
                "Low dead-end logs without a path to leaf-bearing branches should not be included.");
    }

    private static int[][] createNeighborOffsets() {
        int[][] offsets = new int[26][3];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    offsets[index++] = new int[]{dx, dy, dz};
                }
            }
        }
        return offsets;
    }
}

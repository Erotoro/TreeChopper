package me.erotoro.treechopper.tree;

import me.erotoro.treechopper.TreeChopperSettings;
import me.erotoro.treechopper.model.BlockKey;
import me.erotoro.treechopper.model.TreeSearchResult;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.Set;

public final class TreeSearchService {

    private final Set<BlockKey> playerPlacedLogs;
    private final TreeChopperSettings settings;
    private final int[][] neighborOffsets;
    private final int[][] cardinalOffsets;

    public TreeSearchService(Set<BlockKey> playerPlacedLogs, TreeChopperSettings settings, int[][] neighborOffsets, int[][] cardinalOffsets) {
        this.playerPlacedLogs = playerPlacedLogs;
        this.settings = settings;
        this.neighborOffsets = neighborOffsets;
        this.cardinalOffsets = cardinalOffsets;
    }

    public TreeSearchResult findTree(Block start, int maxBreaks) {
        Material logType = start.getType();
        Set<Material> associatedLeaves = TreeMaterials.getAssociatedLeafTypes(logType);
        if (playerPlacedLogs.contains(BlockKey.of(start))) {
            return new TreeSearchResult(Set.of(start), Set.of(BlockKey.of(start)), false, true, false, logType);
        }

        int trunkX = start.getX();
        int trunkZ = start.getZ();
        World world = start.getWorld();
        int baseY = start.getY();
        while (world.getBlockAt(trunkX, baseY - 1, trunkZ).getType() == logType) {
            baseY--;
        }

        List<int[]> trunkColumns = findTrunkColumns(world, logType, baseY, trunkX, trunkZ);
        boolean isMega = trunkColumns.size() == 4;
        Set<BlockKey> trunkBaseBlocks = new LinkedHashSet<>(trunkColumns.size());
        for (int[] column : trunkColumns) {
            trunkBaseBlocks.add(new BlockKey(world.getUID(), column[0], baseY, column[1]));
        }
        Set<Block> treeBlocks = new LinkedHashSet<>();
        int topY = baseY;

        for (int[] column : trunkColumns) {
            int y = baseY;
            while (world.getBlockAt(column[0], y, column[1]).getType() == logType) {
                Block block = world.getBlockAt(column[0], y, column[1]);
                if (playerPlacedLogs.contains(BlockKey.of(block))) {
                    return new TreeSearchResult(treeBlocks, trunkBaseBlocks, false, true, isMega, logType);
                }
                treeBlocks.add(block);
                topY = Math.max(topY, y);
                y++;
            }
        }

        int height = topY - baseY + 1;
        int maxHorizontalDist = isMega ? 8 : 6;
        double trunkCenterX = (isMega ? trunkColumns.stream().mapToDouble(column -> column[0]).average().orElse(trunkX) : trunkX) + 0.5;
        double trunkCenterZ = (isMega ? trunkColumns.stream().mapToDouble(column -> column[1]).average().orElse(trunkZ) : trunkZ) + 0.5;

        int requiredLeafContacts = isMega ? settings.minMegaLeafContacts() : settings.minLeafContacts();
        Set<Block> candidateLogs = collectCandidateLogs(treeBlocks, logType, baseY, trunkCenterX, trunkCenterZ, maxHorizontalDist, maxBreaks);
        int candidateTopY = topY;
        for (Block candidateLog : candidateLogs) {
            candidateTopY = Math.max(candidateTopY, candidateLog.getY());
        }
        Set<Block> leafBearingLogs = collectLeafBearingLogs(candidateLogs, associatedLeaves);
        Set<BlockKey> nearbyLeaves = collectNearbyLeaves(leafBearingLogs, associatedLeaves);

        boolean hasLeafEvidence = nearbyLeaves.size() >= requiredLeafContacts;
        boolean deferNaturalValidationForAcacia = logType == Material.ACACIA_LOG && !hasLeafEvidence;
        if (!deferNaturalValidationForAcacia
                && !NaturalTreeChecker.looksLikeNaturalTree(world, trunkColumns, baseY, candidateTopY, logType, associatedLeaves, settings, cardinalOffsets, hasLeafEvidence)) {
            return new TreeSearchResult(treeBlocks, trunkBaseBlocks, false, false, isMega, logType);
        }

        Set<Block> selectedLogs = selectLogsOnLeafBearingPaths(treeBlocks, candidateLogs, leafBearingLogs);
        for (Block block : selectedLogs) {
            if (playerPlacedLogs.contains(BlockKey.of(block))) {
                return new TreeSearchResult(selectedLogs, trunkBaseBlocks, hasLeafEvidence, true, isMega, logType);
            }
        }
        extendGroundedSupportColumns(selectedLogs, logType, maxBreaks);

        boolean finalLeafEvidence = nearbyLeaves.size() >= requiredLeafContacts;
        if (deferNaturalValidationForAcacia
                && !NaturalTreeChecker.looksLikeNaturalTree(world, trunkColumns, baseY, candidateTopY, logType, associatedLeaves, settings, cardinalOffsets, finalLeafEvidence)) {
            return new TreeSearchResult(selectedLogs, trunkBaseBlocks, false, false, isMega, logType);
        }

        return new TreeSearchResult(selectedLogs, trunkBaseBlocks, finalLeafEvidence, false, isMega, logType);
    }

    private Set<Block> collectCandidateLogs(Set<Block> trunkBlocks, Material logType, int baseY,
                                            double trunkCenterX, double trunkCenterZ, int maxHorizontalDist, int maxBreaks) {
        Set<Block> candidateLogs = new LinkedHashSet<>(trunkBlocks);
        Queue<Block> queue = new ArrayDeque<>(trunkBlocks);
        int minCandidateY = baseY - 4;

        while (!queue.isEmpty() && candidateLogs.size() < maxBreaks && candidateLogs.size() < settings.maxLogs()) {
            Block current = queue.poll();
            for (int[] offset : neighborOffsets) {
                Block neighbor = current.getRelative(offset[0], offset[1], offset[2]);
                if (!neighbor.getChunk().isLoaded()
                        || neighbor.getType() != logType
                        || neighbor.getY() < minCandidateY
                        || !isWithinHorizontalEnvelope(neighbor, trunkCenterX, trunkCenterZ, maxHorizontalDist + 2)
                        || !candidateLogs.add(neighbor)) {
                    continue;
                }
                queue.add(neighbor);
            }
        }

        return candidateLogs;
    }

    private void extendGroundedSupportColumns(Set<Block> treeBlocks, Material logType, int maxBreaks) {
        List<Block> discoveredBlocks = new ArrayList<>(treeBlocks);
        for (Block block : discoveredBlocks) {
            Block current = block;
            while (treeBlocks.size() < maxBreaks && treeBlocks.size() < settings.maxLogs()) {
                Block below = current.getRelative(0, -1, 0);
                if (!below.getChunk().isLoaded() || below.getType() != logType || treeBlocks.contains(below)) {
                    break;
                }
                treeBlocks.add(below);
                current = below;
            }
        }
    }

    private Set<Block> collectLeafBearingLogs(Set<Block> candidateLogs, Set<Material> associatedLeaves) {
        Set<Block> leafBearingLogs = new LinkedHashSet<>();
        for (Block candidateLog : candidateLogs) {
            for (int[] offset : neighborOffsets) {
                Block neighbor = candidateLog.getRelative(offset[0], offset[1], offset[2]);
                if (neighbor.getChunk().isLoaded() && associatedLeaves.contains(neighbor.getType())) {
                    leafBearingLogs.add(candidateLog);
                    break;
                }
            }
        }
        return leafBearingLogs;
    }

    private Set<BlockKey> collectNearbyLeaves(Set<Block> leafBearingLogs, Set<Material> associatedLeaves) {
        Set<BlockKey> nearbyLeaves = new HashSet<>();
        for (Block leafBearingLog : leafBearingLogs) {
            for (int[] offset : neighborOffsets) {
                Block neighbor = leafBearingLog.getRelative(offset[0], offset[1], offset[2]);
                if (neighbor.getChunk().isLoaded() && associatedLeaves.contains(neighbor.getType())) {
                    nearbyLeaves.add(BlockKey.of(neighbor));
                }
            }
        }
        return nearbyLeaves;
    }

    private Set<Block> selectLogsOnLeafBearingPaths(Set<Block> trunkBlocks, Set<Block> candidateLogs, Set<Block> leafBearingLogs) {
        Map<BlockKey, Block> blockByKey = new HashMap<>(candidateLogs.size() * 2);
        Set<BlockKey> candidateKeys = new HashSet<>(candidateLogs.size() * 2);
        for (Block candidateLog : candidateLogs) {
            BlockKey key = BlockKey.of(candidateLog);
            blockByKey.put(key, candidateLog);
            candidateKeys.add(key);
        }

        Queue<Block> queue = new ArrayDeque<>(trunkBlocks);
        Set<BlockKey> visited = new HashSet<>(trunkBlocks.size() * 2);
        Map<BlockKey, BlockKey> parentByChild = new HashMap<>(candidateLogs.size() * 2);
        for (Block trunkBlock : trunkBlocks) {
            visited.add(BlockKey.of(trunkBlock));
        }

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            BlockKey currentKey = BlockKey.of(current);
            for (int[] offset : neighborOffsets) {
                Block neighbor = current.getRelative(offset[0], offset[1], offset[2]);
                BlockKey neighborKey = BlockKey.of(neighbor);
                if (!candidateKeys.contains(neighborKey) || !visited.add(neighborKey)) {
                    continue;
                }
                parentByChild.put(neighborKey, currentKey);
                queue.add(blockByKey.get(neighborKey));
            }
        }

        Set<Block> selectedLogs = new LinkedHashSet<>(trunkBlocks);
        for (Block leafBearingLog : leafBearingLogs) {
            BlockKey currentKey = BlockKey.of(leafBearingLog);
            while (currentKey != null) {
                Block currentBlock = blockByKey.get(currentKey);
                if (currentBlock != null) {
                    selectedLogs.add(currentBlock);
                }
                currentKey = parentByChild.get(currentKey);
            }
        }
        return selectedLogs;
    }

    private boolean isWithinHorizontalEnvelope(Block block, double trunkCenterX, double trunkCenterZ, int maxHorizontalDist) {
        double distX = Math.abs(block.getX() + 0.5 - trunkCenterX);
        double distZ = Math.abs(block.getZ() + 0.5 - trunkCenterZ);
        return distX <= maxHorizontalDist && distZ <= maxHorizontalDist;
    }

    private List<int[]> findTrunkColumns(World world, Material logType, int baseY, int trunkX, int trunkZ) {
        int[][] anchors = new int[][]{
                {trunkX, trunkZ},
                {trunkX - 1, trunkZ},
                {trunkX, trunkZ - 1},
                {trunkX - 1, trunkZ - 1}
        };

        for (int[] anchor : anchors) {
            List<int[]> columns = new ArrayList<>(4);
            boolean valid = true;
            for (int dx = 0; dx <= 1 && valid; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int x = anchor[0] + dx;
                    int z = anchor[1] + dz;
                    if (!hasTrunkColumn(world, logType, baseY, x, z, 4)) {
                        valid = false;
                        break;
                    }
                    columns.add(new int[]{x, z});
                }
            }
            if (valid) {
                return columns;
            }
        }

        return List.of(new int[]{trunkX, trunkZ});
    }

    private boolean hasTrunkColumn(World world, Material logType, int baseY, int x, int z, int minHeight) {
        for (int y = baseY; y < baseY + minHeight; y++) {
            if (world.getBlockAt(x, y, z).getType() != logType) {
                return false;
            }
        }
        return true;
    }
}

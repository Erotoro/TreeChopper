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
        int minBranchY = baseY + (int) (height * 0.3);
        int branchStartY = baseY + (int) (height * 0.4);
        double trunkCenterX = (isMega ? trunkColumns.stream().mapToDouble(column -> column[0]).average().orElse(trunkX) : trunkX) + 0.5;
        double trunkCenterZ = (isMega ? trunkColumns.stream().mapToDouble(column -> column[1]).average().orElse(trunkZ) : trunkZ) + 0.5;

        int requiredLeafContacts = isMega ? settings.minMegaLeafContacts() : settings.minLeafContacts();
        Set<BlockKey> nearbyLeaves = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        for (Block block : treeBlocks) {
            if (block.getY() >= branchStartY) {
                queue.add(block);
            }
        }

        for (Block block : treeBlocks) {
            if (nearbyLeaves.size() >= requiredLeafContacts) {
                break;
            }
            for (int[] offset : neighborOffsets) {
                Block neighbor = block.getRelative(offset[0], offset[1], offset[2]);
                if (neighbor.getChunk().isLoaded() && associatedLeaves.contains(neighbor.getType())) {
                    nearbyLeaves.add(BlockKey.of(neighbor));
                    if (nearbyLeaves.size() >= requiredLeafContacts) {
                        break;
                    }
                }
            }
        }

        boolean hasLeafEvidence = nearbyLeaves.size() >= requiredLeafContacts;
        boolean deferNaturalValidationForAcacia = logType == Material.ACACIA_LOG && !hasLeafEvidence;
        if (!deferNaturalValidationForAcacia
                && !NaturalTreeChecker.looksLikeNaturalTree(world, trunkColumns, baseY, topY, logType, associatedLeaves, settings, cardinalOffsets, hasLeafEvidence)) {
            return new TreeSearchResult(treeBlocks, trunkBaseBlocks, false, false, isMega, logType);
        }

        while (!queue.isEmpty() && treeBlocks.size() < maxBreaks && treeBlocks.size() < settings.maxLogs()) {
            Block current = queue.poll();
            for (int[] offset : neighborOffsets) {
                int dx = offset[0];
                int dy = offset[1];
                int dz = offset[2];

                Block neighbor = current.getRelative(dx, dy, dz);
                if (!neighbor.getChunk().isLoaded()) {
                    continue;
                }

                if (neighbor.getType() == logType && !treeBlocks.contains(neighbor)) {
                    if (neighbor.getY() < minBranchY) {
                        continue;
                    }
                    if (dx != 0 && dz != 0 && dy < 0) {
                        continue;
                    }

                    double distX = Math.abs(neighbor.getX() + 0.5 - trunkCenterX);
                    double distZ = Math.abs(neighbor.getZ() + 0.5 - trunkCenterZ);
                    if (distX > maxHorizontalDist || distZ > maxHorizontalDist) {
                        continue;
                    }
                    if (playerPlacedLogs.contains(BlockKey.of(neighbor))) {
                        return new TreeSearchResult(treeBlocks, trunkBaseBlocks, nearbyLeaves.size() >= requiredLeafContacts, true, isMega, logType);
                    }

                    treeBlocks.add(neighbor);
                    queue.add(neighbor);
                } else if (associatedLeaves.contains(neighbor.getType())) {
                    nearbyLeaves.add(BlockKey.of(neighbor));
                }
            }
        }

        boolean finalLeafEvidence = nearbyLeaves.size() >= requiredLeafContacts;
        if (deferNaturalValidationForAcacia
                && !NaturalTreeChecker.looksLikeNaturalTree(world, trunkColumns, baseY, topY, logType, associatedLeaves, settings, cardinalOffsets, finalLeafEvidence)) {
            return new TreeSearchResult(treeBlocks, trunkBaseBlocks, false, false, isMega, logType);
        }

        return new TreeSearchResult(treeBlocks, trunkBaseBlocks, finalLeafEvidence, false, isMega, logType);
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

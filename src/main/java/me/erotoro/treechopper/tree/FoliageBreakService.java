package me.erotoro.treechopper.tree;

import me.erotoro.treechopper.TreeChopperSettings;
import me.erotoro.treechopper.coreprotect.CoreProtectService;
import me.erotoro.treechopper.scheduler.TaskSchedulerFacade;
import me.erotoro.treechopper.util.CoordinatePacker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;

public final class FoliageBreakService {

    private final TreeChopperSettings settings;
    private final TaskSchedulerFacade scheduler;
    private final int[][] neighborOffsets;
    private final BiFunction<Player, Block, BlockBreakEvent> syntheticBreakFactory;
    private final CoreProtectService coreProtectService;

    public FoliageBreakService(TreeChopperSettings settings, TaskSchedulerFacade scheduler, int[][] neighborOffsets,
                               BiFunction<Player, Block, BlockBreakEvent> syntheticBreakFactory,
                               CoreProtectService coreProtectService) {
        this.settings = settings;
        this.scheduler = scheduler;
        this.neighborOffsets = neighborOffsets;
        this.syntheticBreakFactory = syntheticBreakFactory;
        this.coreProtectService = coreProtectService;
    }

    public void breakFoliage(Player player, Set<Location> logPositions, Set<Block> treeBlocks, Material logType) {
        if (logPositions.isEmpty()) {
            return;
        }

        Location sample = logPositions.iterator().next();
        World world = sample.getWorld();
        if (world == null) {
            return;
        }

        Set<Material> validLeaves = TreeMaterials.getAssociatedLeafTypes(logType);
        Set<Block> leavesToBreak = new HashSet<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        for (Location logLocation : logPositions) {
            Block logBlock = world.getBlockAt(logLocation);
            for (int[] offset : neighborOffsets) {
                Block neighbor = logBlock.getRelative(offset[0], offset[1], offset[2]);
                if (neighbor.getChunk().isLoaded() && validLeaves.contains(neighbor.getType()) && visited.add(neighbor)) {
                    queue.add(neighbor);
                    leavesToBreak.add(neighbor);
                }
            }
        }

        int depth = 1;
        while (!queue.isEmpty() && depth < settings.leafSearchRadius()) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                Block current = queue.poll();
                for (int[] offset : neighborOffsets) {
                    Block neighbor = current.getRelative(offset[0], offset[1], offset[2]);
                    if (neighbor.getChunk().isLoaded() && validLeaves.contains(neighbor.getType()) && visited.add(neighbor)) {
                        queue.add(neighbor);
                        leavesToBreak.add(neighbor);
                    }
                }
            }
            depth++;
        }

        filterLeavesOwnedByOtherTrees(world, leavesToBreak, treeBlocks, logType);
        Set<Block> decorationsToBreak = collectDecorationsToBreak(logPositions, leavesToBreak);
        Set<Block> foliageToBreak = new HashSet<>(leavesToBreak.size() + decorationsToBreak.size());
        foliageToBreak.addAll(leavesToBreak);
        foliageToBreak.addAll(decorationsToBreak);

        if (foliageToBreak.isEmpty()) {
            return;
        }

        TreeMap<Integer, List<Block>> byHeight = new TreeMap<>(Comparator.reverseOrder());
        for (Block foliageBlock : foliageToBreak) {
            byHeight.computeIfAbsent(foliageBlock.getY(), ignored -> new ArrayList<>()).add(foliageBlock);
        }

        int layerIndex = 0;
        for (List<Block> layer : byHeight.values()) {
            long delayTicks = layerIndex * 2L;
            scheduler.scheduleBlockBatches(layer, delayTicks, settings.maxBlocksPerTask(), batch -> {
                for (Block leaf : batch) {
                    Material type = leaf.getType();
                    if (!leaf.getChunk().isLoaded() || (!validLeaves.contains(type) && !TreeMaterials.isTreeDecoration(type))) {
                        continue;
                    }
                    BlockBreakEvent syntheticBreak = syntheticBreakFactory.apply(player, leaf);
                    if (syntheticBreak.isCancelled()) {
                        continue;
                    }
                    BlockState originalState = leaf.getState();
                    if (syntheticBreak.isDropItems()) {
                        leaf.breakNaturally();
                    } else {
                        leaf.setType(Material.AIR, false);
                    }
                    if (!leaf.getType().isAir()) {
                        continue;
                    }
                    coreProtectService.logRemoval(player.getName(), originalState);
                }
            });
            layerIndex++;
        }
    }

    private Set<Block> collectDecorationsToBreak(Set<Location> logPositions, Set<Block> leavesToBreak) {
        Set<Block> decorations = new HashSet<>();
        Set<Block> visited = new HashSet<>();

        for (Location logLocation : logPositions) {
            Block logBlock = logLocation.getBlock();
            collectDecorationsAroundSeed(logBlock, decorations, visited);
        }
        for (Block leaf : leavesToBreak) {
            collectDecorationsAroundSeed(leaf, decorations, visited);
        }

        return decorations;
    }

    private void collectDecorationsAroundSeed(Block seed, Set<Block> decorations, Set<Block> visited) {
        for (int[] offset : neighborOffsets) {
            Block neighbor = seed.getRelative(offset[0], offset[1], offset[2]);
            if (TreeMaterials.isTreeDecoration(neighbor.getType())) {
                collectDecorationCluster(neighbor, decorations, visited);
            }
        }

        Block below = seed.getRelative(0, -1, 0);
        if (TreeMaterials.isTreeDecoration(below.getType())) {
            collectDecorationCluster(below, decorations, visited);
        }
    }

    private void collectDecorationCluster(Block start, Set<Block> decorations, Set<Block> visited) {
        Queue<Block> queue = new ArrayDeque<>();
        if (!visited.add(start)) {
            return;
        }
        queue.add(start);

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            if (!current.getChunk().isLoaded() || !TreeMaterials.isTreeDecoration(current.getType())) {
                continue;
            }
            decorations.add(current);

            for (int[] offset : neighborOffsets) {
                Block neighbor = current.getRelative(offset[0], offset[1], offset[2]);
                if (TreeMaterials.isTreeDecoration(neighbor.getType()) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }

            Block below = current.getRelative(0, -1, 0);
            if (TreeMaterials.isTreeDecoration(below.getType()) && visited.add(below)) {
                queue.add(below);
            }
        }
    }

    private void filterLeavesOwnedByOtherTrees(World world, Set<Block> leavesToBreak, Set<Block> treeBlocks, Material logType) {
        Set<Long> ourLogPacked = new HashSet<>(treeBlocks.size() * 2);
        for (Block block : treeBlocks) {
            ourLogPacked.add(CoordinatePacker.pack(block.getX(), block.getY(), block.getZ()));
        }

        Set<Long> foreignLogPacked = new HashSet<>();
        Set<Long> checkedPositions = new HashSet<>(ourLogPacked);
        for (Block leaf : leavesToBreak) {
            int leafX = leaf.getX();
            int leafY = leaf.getY();
            int leafZ = leaf.getZ();
            for (int dx = -settings.foreignLogScanRadius(); dx <= settings.foreignLogScanRadius(); dx++) {
                for (int dy = -settings.foreignLogScanRadius(); dy <= settings.foreignLogScanRadius(); dy++) {
                    for (int dz = -settings.foreignLogScanRadius(); dz <= settings.foreignLogScanRadius(); dz++) {
                        int x = leafX + dx;
                        int y = leafY + dy;
                        int z = leafZ + dz;
                        long packed = CoordinatePacker.pack(x, y, z);
                        if (!checkedPositions.add(packed)) {
                            continue;
                        }
                        if (world.getBlockAt(x, y, z).getType() == logType) {
                            foreignLogPacked.add(packed);
                        }
                    }
                }
            }
        }

        if (foreignLogPacked.isEmpty()) {
            return;
        }

        int[][] ourCoords = new int[treeBlocks.size()][3];
        int index = 0;
        for (Block block : treeBlocks) {
            ourCoords[index][0] = block.getX();
            ourCoords[index][1] = block.getY();
            ourCoords[index][2] = block.getZ();
            index++;
        }

        int[][] foreignCoords = new int[foreignLogPacked.size()][3];
        index = 0;
        for (long packed : foreignLogPacked) {
            foreignCoords[index][0] = CoordinatePacker.unpackX(packed);
            foreignCoords[index][1] = CoordinatePacker.unpackY(packed);
            foreignCoords[index][2] = CoordinatePacker.unpackZ(packed);
            index++;
        }

        leavesToBreak.removeIf(leaf -> {
            int leafX = leaf.getX();
            int leafY = leaf.getY();
            int leafZ = leaf.getZ();

            int minOurDistance = Integer.MAX_VALUE;
            for (int[] coords : ourCoords) {
                int distance = Math.abs(leafX - coords[0]) + Math.abs(leafY - coords[1]) + Math.abs(leafZ - coords[2]);
                minOurDistance = Math.min(minOurDistance, distance);
                if (minOurDistance <= 1) {
                    break;
                }
            }

            int minForeignDistance = Integer.MAX_VALUE;
            for (int[] coords : foreignCoords) {
                int distance = Math.abs(leafX - coords[0]) + Math.abs(leafY - coords[1]) + Math.abs(leafZ - coords[2]);
                minForeignDistance = Math.min(minForeignDistance, distance);
                if (minForeignDistance <= 1) {
                    break;
                }
            }

            return minForeignDistance <= minOurDistance;
        });
    }
}

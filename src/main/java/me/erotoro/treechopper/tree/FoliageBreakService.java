package me.erotoro.treechopper.tree;

import me.erotoro.treechopper.TreeChopperSettings;
import me.erotoro.treechopper.coreprotect.CoreProtectService;
import me.erotoro.treechopper.model.ChunkKey;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Breaks foliage (leaves and decorations such as vines, mangrove roots, propagules)
 * that belongs to the tree we just felled.
 *
 * <p>The work is dispatched per-chunk: each chunk's processing runs on its own region
 * thread via {@link TaskSchedulerFacade#scheduleDelayed}. This is required for Folia
 * where accessing a block outside the current region's owned chunks throws an
 * {@code IllegalStateException}. The per-chunk model accepts a minor coverage trade-off
 * (leaves dangling into chunks without logs may be missed) in exchange for thread safety.
 */
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

        // Coordinate snapshots — Block/Location references mustn't be dereferenced across
        // regions on Folia, but raw ints are safe to pass to any scheduled task.
        Set<Long> allTreeBlockCoords = packBlockCoords(treeBlocks);
        List<int[]> allLogCoords = new ArrayList<>(logPositions.size());
        int minLogX = Integer.MAX_VALUE;
        int maxLogX = Integer.MIN_VALUE;
        int minLogZ = Integer.MAX_VALUE;
        int maxLogZ = Integer.MIN_VALUE;
        for (Location loc : logPositions) {
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            allLogCoords.add(new int[]{x, y, z});
            if (x < minLogX) minLogX = x;
            if (x > maxLogX) maxLogX = x;
            if (z < minLogZ) minLogZ = z;
            if (z > maxLogZ) maxLogZ = z;
        }

        // Dispatch a per-chunk task for every chunk that could contain foliage attached
        // to this tree — i.e. chunks within `leafSearchRadius` of any log. This covers
        // mega-tree canopies that overflow into chunks containing no logs themselves.
        int reach = settings.leafSearchRadius();
        int chunkMinX = (minLogX - reach) >> 4;
        int chunkMaxX = (maxLogX + reach) >> 4;
        int chunkMinZ = (minLogZ - reach) >> 4;
        int chunkMaxZ = (maxLogZ + reach) >> 4;

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                int chunkX = cx;
                int chunkZ = cz;
                // Schedule at the centre of the target chunk so Folia's RegionScheduler
                // routes the task to that chunk's owning region.
                Location anchor = new Location(world, (chunkX << 4) + 8, sample.getBlockY(), (chunkZ << 4) + 8);
                scheduler.scheduleDelayed(anchor, 0L, () ->
                        processChunkFoliage(player, world, chunkX, chunkZ, allLogCoords,
                                validLeaves, allTreeBlockCoords, logType)
                );
            }
        }
    }

    private void processChunkFoliage(Player player, World world, int chunkX, int chunkZ,
                                     List<int[]> allLogCoords, Set<Material> validLeaves,
                                     Set<Long> allTreeBlockCoords, Material logType) {
        Set<Block> leavesToBreak = bfsLeavesInChunk(world, allLogCoords, validLeaves, chunkX, chunkZ);
        filterLeavesOwnedByForeignLogsInChunk(world, leavesToBreak, allTreeBlockCoords,
                logType, chunkX, chunkZ);

        Set<Block> decorations = collectDecorationsInChunk(world, allLogCoords, leavesToBreak,
                chunkX, chunkZ);
        decorations = filterDecorationsOwnedByCurrentTreeInChunk(world, decorations, leavesToBreak,
                allTreeBlockCoords, logType, chunkX, chunkZ);

        Set<Block> foliage = new HashSet<>(leavesToBreak.size() + decorations.size());
        foliage.addAll(leavesToBreak);
        foliage.addAll(decorations);
        if (foliage.isEmpty()) {
            return;
        }

        scheduleFoliageBreaks(player, foliage, validLeaves);
    }

    private Set<Block> bfsLeavesInChunk(World world, List<int[]> allLogCoords, Set<Material> validLeaves,
                                        int chunkX, int chunkZ) {
        Set<Block> leavesToBreak = new HashSet<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        // Seed: leaves IN OUR CHUNK that are adjacent to ANY log (even logs in other chunks).
        // We only dereference world.getBlockAt for coordinates inside our chunk, so this stays
        // Folia-safe while still catching canopy that hangs over into log-free chunks.
        for (int[] log : allLogCoords) {
            int lx = log[0];
            int ly = log[1];
            int lz = log[2];
            for (int[] offset : neighborOffsets) {
                int nx = lx + offset[0];
                int ny = ly + offset[1];
                int nz = lz + offset[2];
                if ((nx >> 4) != chunkX || (nz >> 4) != chunkZ) {
                    continue;
                }
                Block neighbor = world.getBlockAt(nx, ny, nz);
                if (validLeaves.contains(neighbor.getType()) && visited.add(neighbor)) {
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
                    if (!isInChunk(neighbor, chunkX, chunkZ)) {
                        continue;
                    }
                    if (validLeaves.contains(neighbor.getType()) && visited.add(neighbor)) {
                        queue.add(neighbor);
                        leavesToBreak.add(neighbor);
                    }
                }
            }
            depth++;
        }

        return leavesToBreak;
    }

    private void scheduleFoliageBreaks(Player player, Set<Block> foliage, Set<Material> validLeaves) {
        TreeMap<Integer, List<Block>> byHeight = new TreeMap<>(Comparator.reverseOrder());
        for (Block foliageBlock : foliage) {
            byHeight.computeIfAbsent(foliageBlock.getY(), ignored -> new ArrayList<>()).add(foliageBlock);
        }

        int layerIndex = 0;
        for (List<Block> layer : byHeight.values()) {
            long delayTicks = layerIndex * 2L;
            scheduler.scheduleBlockBatches(layer, delayTicks, settings.maxBlocksPerTask(), batch -> {
                for (Block leaf : batch) {
                    Material type = leaf.getType();
                    if (!validLeaves.contains(type) && !TreeMaterials.isTreeDecoration(type)) {
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
                    if (!TreeMaterials.isAir(leaf.getType())) {
                        continue;
                    }
                    coreProtectService.logRemoval(player.getName(), originalState);
                }
            });
            layerIndex++;
        }
    }

    private Set<Block> collectDecorationsInChunk(World world, List<int[]> allLogCoords, Set<Block> leavesToBreak,
                                                 int chunkX, int chunkZ) {
        Set<Block> decorations = new HashSet<>();
        Set<Block> visited = new HashSet<>();

        // Decorations rooted in our chunk that are adjacent to ANY log (any chunk) or to
        // a leaf already discovered in our chunk.
        for (int[] log : allLogCoords) {
            int lx = log[0];
            int ly = log[1];
            int lz = log[2];
            for (int[] offset : neighborOffsets) {
                int nx = lx + offset[0];
                int ny = ly + offset[1];
                int nz = lz + offset[2];
                if ((nx >> 4) != chunkX || (nz >> 4) != chunkZ) {
                    continue;
                }
                Block neighbor = world.getBlockAt(nx, ny, nz);
                if (TreeMaterials.isTreeDecoration(neighbor.getType())) {
                    collectDecorationCluster(neighbor, decorations, visited, chunkX, chunkZ);
                }
            }
            // The block directly below a log (mangrove propagules, hanging moss, etc.).
            int bx = lx;
            int by = ly - 1;
            int bz = lz;
            if ((bx >> 4) == chunkX && (bz >> 4) == chunkZ) {
                Block below = world.getBlockAt(bx, by, bz);
                if (TreeMaterials.isTreeDecoration(below.getType())) {
                    collectDecorationCluster(below, decorations, visited, chunkX, chunkZ);
                }
            }
        }
        for (Block leaf : leavesToBreak) {
            collectDecorationsAroundSeed(leaf, decorations, visited, chunkX, chunkZ);
        }

        return decorations;
    }

    private void collectDecorationsAroundSeed(Block seed, Set<Block> decorations, Set<Block> visited,
                                              int chunkX, int chunkZ) {
        for (int[] offset : neighborOffsets) {
            Block neighbor = seed.getRelative(offset[0], offset[1], offset[2]);
            if (!isInChunk(neighbor, chunkX, chunkZ)) {
                continue;
            }
            if (TreeMaterials.isTreeDecoration(neighbor.getType())) {
                collectDecorationCluster(neighbor, decorations, visited, chunkX, chunkZ);
            }
        }

        Block below = seed.getRelative(0, -1, 0);
        if (isInChunk(below, chunkX, chunkZ) && TreeMaterials.isTreeDecoration(below.getType())) {
            collectDecorationCluster(below, decorations, visited, chunkX, chunkZ);
        }
    }

    private void collectDecorationCluster(Block start, Set<Block> decorations, Set<Block> visited,
                                          int chunkX, int chunkZ) {
        Queue<Block> queue = new ArrayDeque<>();
        if (!visited.add(start)) {
            return;
        }
        queue.add(start);

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            if (!TreeMaterials.isTreeDecoration(current.getType())) {
                continue;
            }
            decorations.add(current);

            for (int[] offset : neighborOffsets) {
                Block neighbor = current.getRelative(offset[0], offset[1], offset[2]);
                if (!isInChunk(neighbor, chunkX, chunkZ)) {
                    continue;
                }
                if (TreeMaterials.isTreeDecoration(neighbor.getType()) && visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }

            Block below = current.getRelative(0, -1, 0);
            if (isInChunk(below, chunkX, chunkZ)
                    && TreeMaterials.isTreeDecoration(below.getType())
                    && visited.add(below)) {
                queue.add(below);
            }
        }
    }

    private Set<Block> filterDecorationsOwnedByCurrentTreeInChunk(World world, Set<Block> decorationsToBreak,
                                                                  Set<Block> leavesToBreak,
                                                                  Set<Long> allTreeBlockCoords,
                                                                  Material logType,
                                                                  int chunkX, int chunkZ) {
        if (decorationsToBreak.isEmpty()) {
            return decorationsToBreak;
        }

        Set<Block> retainedDecorations = new HashSet<>();
        Set<Block> mangroveRootCandidates = new LinkedHashSet<>();
        Set<Block> genericDecorationCandidates = new LinkedHashSet<>();

        for (Block decoration : decorationsToBreak) {
            Material type = decoration.getType();
            if (TreeMaterials.isMangroveRootDecoration(type)) {
                mangroveRootCandidates.add(decoration);
            } else if (TreeMaterials.isMangrovePropagule(type)) {
                if (isPropaguleOwnedByCurrentTree(decoration, leavesToBreak)) {
                    retainedDecorations.add(decoration);
                }
            } else {
                genericDecorationCandidates.add(decoration);
            }
        }

        retainedDecorations.addAll(selectOwnedGenericDecorations(world, genericDecorationCandidates,
                leavesToBreak, allTreeBlockCoords, chunkX, chunkZ));
        if (TreeMaterials.isMangroveLog(logType)) {
            retainedDecorations.addAll(selectOwnedMangroveRoots(world, mangroveRootCandidates,
                    allTreeBlockCoords, logType, chunkX, chunkZ));
        }
        return retainedDecorations;
    }

    private boolean isPropaguleOwnedByCurrentTree(Block propagule, Set<Block> leavesToBreak) {
        Block above = propagule.getRelative(0, 1, 0);
        return leavesToBreak.contains(above);
    }

    private Set<Block> selectOwnedGenericDecorations(World world, Set<Block> decorationCandidates,
                                                     Set<Block> leavesToBreak, Set<Long> allTreeBlockCoords,
                                                     int chunkX, int chunkZ) {
        if (decorationCandidates.isEmpty()) {
            return Set.of();
        }

        Set<Long> ownSeedCoords = new HashSet<>(allTreeBlockCoords);
        for (Block leaf : leavesToBreak) {
            ownSeedCoords.add(CoordinatePacker.pack(leaf.getX(), leaf.getY(), leaf.getZ()));
        }

        Set<Long> foreignSeedCoords = collectNearbyForeignTreeSeedsInChunk(world, decorationCandidates,
                ownSeedCoords, chunkX, chunkZ);
        Set<Block> retainedDecorations = new LinkedHashSet<>();
        Set<Material> materialTypes = new HashSet<>();
        for (Block candidate : decorationCandidates) {
            materialTypes.add(candidate.getType());
        }

        for (Material materialType : materialTypes) {
            retainedDecorations.addAll(selectOwnedDecorationMaterial(decorationCandidates, materialType,
                    ownSeedCoords, foreignSeedCoords, chunkX, chunkZ));
        }
        return retainedDecorations;
    }

    private Set<Block> selectOwnedDecorationMaterial(Set<Block> decorationCandidates, Material materialType,
                                                     Set<Long> ownSeedCoords, Set<Long> foreignSeedCoords,
                                                     int chunkX, int chunkZ) {
        Set<Block> materialCandidates = new LinkedHashSet<>();
        for (Block candidate : decorationCandidates) {
            if (candidate.getType() == materialType) {
                materialCandidates.add(candidate);
            }
        }
        if (materialCandidates.isEmpty()) {
            return Set.of();
        }

        Set<Block> retained = new LinkedHashSet<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        for (Block candidate : materialCandidates) {
            if (!isDecorationAnchor(candidate, ownSeedCoords, chunkX, chunkZ)) {
                continue;
            }
            if (!visited.add(candidate)) {
                continue;
            }
            if (!isCloserToCurrentTree(candidate, ownSeedCoords, foreignSeedCoords, true)) {
                continue;
            }
            retained.add(candidate);
            queue.add(candidate);
        }

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            for (int[] offset : neighborOffsets) {
                Block neighbor = current.getRelative(offset[0], offset[1], offset[2]);
                if (!isInChunk(neighbor, chunkX, chunkZ)) {
                    continue;
                }
                if (!materialCandidates.contains(neighbor) || !visited.add(neighbor)) {
                    continue;
                }
                if (!isCloserToCurrentTree(neighbor, ownSeedCoords, foreignSeedCoords, false)) {
                    continue;
                }
                retained.add(neighbor);
                queue.add(neighbor);
            }

            Block below = current.getRelative(0, -1, 0);
            if (isInChunk(below, chunkX, chunkZ)
                    && materialCandidates.contains(below)
                    && visited.add(below)
                    && isCloserToCurrentTree(below, ownSeedCoords, foreignSeedCoords, false)) {
                retained.add(below);
                queue.add(below);
            }
        }

        return retained;
    }

    private Set<Long> collectNearbyForeignTreeSeedsInChunk(World world, Set<Block> candidates,
                                                            Set<Long> ownSeedCoords,
                                                            int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxZ = chunkMinZ + 15;
        int radius = settings.foreignLogScanRadius();

        Set<Long> foreignSeeds = new HashSet<>();
        Set<Long> scanned = new HashSet<>(ownSeedCoords);
        for (Block candidate : candidates) {
            int cx = candidate.getX();
            int cy = candidate.getY();
            int cz = candidate.getZ();
            int minX = Math.max(chunkMinX, cx - radius);
            int maxX = Math.min(chunkMaxX, cx + radius);
            int minZ = Math.max(chunkMinZ, cz - radius);
            int maxZ = Math.min(chunkMaxZ, cz + radius);
            for (int x = minX; x <= maxX; x++) {
                for (int y = cy - radius; y <= cy + radius; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        long packed = CoordinatePacker.pack(x, y, z);
                        if (!scanned.add(packed)) {
                            continue;
                        }
                        Block scannedBlock = world.getBlockAt(x, y, z);
                        Material type = scannedBlock.getType();
                        if (TreeMaterials.isLog(type) || TreeMaterials.isLeafMaterial(type)) {
                            foreignSeeds.add(packed);
                        }
                    }
                }
            }
        }

        return foreignSeeds;
    }

    private boolean isDecorationAnchor(Block decoration, Set<Long> ownSeedCoords,
                                       int chunkX, int chunkZ) {
        for (int[] offset : neighborOffsets) {
            Block neighbor = decoration.getRelative(offset[0], offset[1], offset[2]);
            if (ownSeedCoords.contains(CoordinatePacker.pack(neighbor.getX(), neighbor.getY(), neighbor.getZ()))) {
                return true;
            }
        }
        Block below = decoration.getRelative(0, -1, 0);
        return ownSeedCoords.contains(CoordinatePacker.pack(below.getX(), below.getY(), below.getZ()));
    }

    private Set<Block> selectOwnedMangroveRoots(World world, Set<Block> rootCandidates,
                                                Set<Long> allTreeBlockCoords, Material logType,
                                                int chunkX, int chunkZ) {
        if (rootCandidates.isEmpty()) {
            return Set.of();
        }

        Set<Long> foreignLogs = collectNearbyForeignLogsInChunk(world, rootCandidates, allTreeBlockCoords,
                logType, chunkX, chunkZ);
        Set<Block> retainedRoots = new LinkedHashSet<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        for (Block candidate : rootCandidates) {
            if (isRootAnchoredToCurrentTree(candidate, allTreeBlockCoords)
                    && isCloserToCurrentTree(candidate, allTreeBlockCoords, foreignLogs, true)) {
                retainedRoots.add(candidate);
                visited.add(candidate);
                queue.add(candidate);
            }
        }

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            for (int[] offset : neighborOffsets) {
                Block neighbor = current.getRelative(offset[0], offset[1], offset[2]);
                if (!isInChunk(neighbor, chunkX, chunkZ)) {
                    continue;
                }
                if (!rootCandidates.contains(neighbor) || !visited.add(neighbor)) {
                    continue;
                }
                if (!isCloserToCurrentTree(neighbor, allTreeBlockCoords, foreignLogs, false)) {
                    continue;
                }
                retainedRoots.add(neighbor);
                queue.add(neighbor);
            }
        }

        return retainedRoots;
    }

    private Set<Long> collectNearbyForeignLogsInChunk(World world, Set<Block> candidates,
                                                       Set<Long> ownLogCoords, Material logType,
                                                       int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxZ = chunkMinZ + 15;
        int radius = settings.foreignLogScanRadius();

        Set<Long> foreignLogs = new HashSet<>();
        Set<Long> scanned = new HashSet<>(ownLogCoords);
        for (Block candidate : candidates) {
            int cx = candidate.getX();
            int cy = candidate.getY();
            int cz = candidate.getZ();
            int minX = Math.max(chunkMinX, cx - radius);
            int maxX = Math.min(chunkMaxX, cx + radius);
            int minZ = Math.max(chunkMinZ, cz - radius);
            int maxZ = Math.min(chunkMaxZ, cz + radius);
            for (int x = minX; x <= maxX; x++) {
                for (int y = cy - radius; y <= cy + radius; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        long packed = CoordinatePacker.pack(x, y, z);
                        if (!scanned.add(packed)) {
                            continue;
                        }
                        if (world.getBlockAt(x, y, z).getType() == logType) {
                            foreignLogs.add(packed);
                        }
                    }
                }
            }
        }

        return foreignLogs;
    }

    private boolean isRootAnchoredToCurrentTree(Block root, Set<Long> allTreeBlockCoords) {
        int rx = root.getX();
        int ry = root.getY();
        int rz = root.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (allTreeBlockCoords.contains(CoordinatePacker.pack(rx + dx, ry + dy, rz + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isCloserToCurrentTree(Block block, Set<Long> ownSeedCoords, Set<Long> foreignCoords,
                                          boolean allowEqualDistanceForAnchor) {
        if (foreignCoords.isEmpty()) {
            return true;
        }
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        int ourDistance = minManhattanDistance(bx, by, bz, ownSeedCoords);
        int foreignDistance = minManhattanDistance(bx, by, bz, foreignCoords);
        if (ourDistance < foreignDistance) {
            return true;
        }
        return allowEqualDistanceForAnchor && ourDistance == foreignDistance;
    }

    private int minManhattanDistance(int x, int y, int z, Set<Long> packedTargets) {
        int min = Integer.MAX_VALUE;
        for (long packed : packedTargets) {
            int tx = CoordinatePacker.unpackX(packed);
            int ty = CoordinatePacker.unpackY(packed);
            int tz = CoordinatePacker.unpackZ(packed);
            int distance = Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz);
            if (distance < min) {
                min = distance;
                if (min <= 1) {
                    return min;
                }
            }
        }
        return min;
    }

    private void filterLeavesOwnedByForeignLogsInChunk(World world, Set<Block> leavesToBreak,
                                                       Set<Long> allTreeBlockCoords, Material logType,
                                                       int chunkX, int chunkZ) {
        if (leavesToBreak.isEmpty()) {
            return;
        }
        int chunkMinX = chunkX << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxZ = chunkMinZ + 15;
        int radius = settings.foreignLogScanRadius();

        Set<Long> foreignLogPacked = new HashSet<>();
        Set<Long> scanned = new HashSet<>(allTreeBlockCoords);
        for (Block leaf : leavesToBreak) {
            int lx = leaf.getX();
            int ly = leaf.getY();
            int lz = leaf.getZ();
            int minX = Math.max(chunkMinX, lx - radius);
            int maxX = Math.min(chunkMaxX, lx + radius);
            int minZ = Math.max(chunkMinZ, lz - radius);
            int maxZ = Math.min(chunkMaxZ, lz + radius);
            for (int x = minX; x <= maxX; x++) {
                for (int y = ly - radius; y <= ly + radius; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        long packed = CoordinatePacker.pack(x, y, z);
                        if (!scanned.add(packed)) {
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

        leavesToBreak.removeIf(leaf -> {
            int lx = leaf.getX();
            int ly = leaf.getY();
            int lz = leaf.getZ();
            int minOurDistance = minManhattanDistance(lx, ly, lz, allTreeBlockCoords);
            int minForeignDistance = minManhattanDistance(lx, ly, lz, foreignLogPacked);
            return minForeignDistance <= minOurDistance;
        });
    }

    private static boolean isInChunk(Block block, int chunkX, int chunkZ) {
        return (block.getX() >> 4) == chunkX && (block.getZ() >> 4) == chunkZ;
    }

    private static Set<Long> packBlockCoords(Set<Block> blocks) {
        Set<Long> packed = new HashSet<>(blocks.size() * 2);
        for (Block block : blocks) {
            packed.add(CoordinatePacker.pack(block.getX(), block.getY(), block.getZ()));
        }
        return packed;
    }
}

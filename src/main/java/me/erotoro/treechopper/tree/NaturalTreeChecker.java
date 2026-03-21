package me.erotoro.treechopper.tree;

import me.erotoro.treechopper.TreeChopperSettings;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Set;

public final class NaturalTreeChecker {

    private static final int STRUCTURE_PROBE_HEIGHT = 4;

    private NaturalTreeChecker() {
    }

    public static boolean looksLikeNaturalTree(World world, List<int[]> trunkColumns, int baseY, int topY,
                                              Material logType, Set<Material> associatedLeaves, TreeChopperSettings settings,
                                              int[][] cardinalOffsets, boolean hasLeafEvidence) {
        if (!hasLeafEvidence) {
            return false;
        }

        if (hasArtificialFoundation(world, trunkColumns, baseY, logType, associatedLeaves)
                && hasDenseStructureContacts(world, trunkColumns, baseY, topY, logType, associatedLeaves, settings, cardinalOffsets)) {
            return false;
        }

        return true;
    }

    private static boolean hasArtificialFoundation(World world, List<int[]> trunkColumns, int baseY,
                                                   Material logType, Set<Material> associatedLeaves) {
        for (int[] column : trunkColumns) {
            Material belowType = world.getBlockAt(column[0], baseY - 1, column[1]).getType();
            if (belowType.isAir()
                    || belowType == logType
                    || TreeMaterials.isLog(belowType)
                    || associatedLeaves.contains(belowType)
                    || TreeMaterials.isTreeDecoration(belowType)
                    || isNaturalTerrain(belowType)
                    || isNaturalSupportMaterial(belowType)
                    || !belowType.isSolid()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDenseStructureContacts(World world, List<int[]> trunkColumns, int baseY, int topY,
                                                     Material logType, Set<Material> associatedLeaves,
                                                     TreeChopperSettings settings, int[][] cardinalOffsets) {
        if (settings.maxStructureContacts() <= 0) {
            return false;
        }
        int probeTopY = Math.min(topY, baseY + Math.max(1, STRUCTURE_PROBE_HEIGHT - 1));
        int structureContacts = 0;

        for (int[] column : trunkColumns) {
            for (int y = baseY; y <= probeTopY; y++) {
                for (int[] offset : cardinalOffsets) {
                    Material neighborType = world.getBlockAt(column[0] + offset[0], y, column[1] + offset[1]).getType();
                    if (isStructureContactMaterial(neighborType, logType, associatedLeaves)) {
                        structureContacts++;
                        if (structureContacts >= settings.maxStructureContacts()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isStructureContactMaterial(Material material, Material logType, Set<Material> associatedLeaves) {
        if (material.isAir() || isNaturalTerrain(material) || !material.isSolid()) {
            return false;
        }
        if (material == logType || TreeMaterials.isLog(material) || associatedLeaves.contains(material) || TreeMaterials.isTreeDecoration(material)) {
            return false;
        }
        if (material.name().endsWith("_LEAVES")) {
            return false;
        }
        return true;
    }

    private static boolean isNaturalSupportMaterial(Material material) {
        String name = material.name();
        return name.equals("MANGROVE_ROOTS")
                || name.equals("MUDDY_MANGROVE_ROOTS")
                || name.equals("ROOTED_DIRT")
                || name.equals("MOSS_BLOCK")
                || name.equals("MOSS_CARPET")
                || name.equals("NETHERRACK")
                || name.equals("CRIMSON_NYLIUM")
                || name.equals("WARPED_NYLIUM")
                || name.equals("SOUL_SOIL")
                || name.equals("SOUL_SAND");
    }

    private static boolean isNaturalTerrain(Material material) {
        String name = material.name();
        return name.endsWith("_DIRT")
                || name.endsWith("_GRASS_BLOCK")
                || name.endsWith("_STONE")
                || name.endsWith("_SAND")
                || name.endsWith("_CLAY")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_SNOW")
                || name.endsWith("_ICE")
                || name.endsWith("_TUFF")
                || name.endsWith("_GRAVEL")
                || name.endsWith("_DEEPSLATE")
                || name.endsWith("_ANDESITE")
                || name.endsWith("_DIORITE")
                || name.endsWith("_GRANITE")
                || name.endsWith("_TERRACOTTA")
                || name.equals("GRASS_BLOCK")
                || name.equals("PODZOL")
                || name.equals("MYCELIUM")
                || name.equals("MOSS_BLOCK")
                || name.equals("MUD")
                || name.equals("MUDDY_MANGROVE_ROOTS")
                || name.equals("DIRT_PATH")
                || name.equals("FARMLAND")
                || name.equals("COARSE_DIRT")
                || name.equals("PODZOL")
                || name.equals("ROOTED_DIRT")
                || name.equals("MANGROVE_ROOTS");
    }
}

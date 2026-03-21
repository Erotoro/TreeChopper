package me.erotoro.treechopper.tree;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TreeMaterials {

    private static final Set<Material> LOG_MATERIALS = new HashSet<>();
    private static final Set<Material> LEAF_MATERIALS = new HashSet<>();
    private static final Set<Material> TREE_DECORATION_MATERIALS = new HashSet<>();
    private static final Map<Material, Set<Material>> LEAF_TYPE_CACHE = new HashMap<>();

    static {
        for (Material material : Material.values()) {
            String name = material.name();
            if (!name.startsWith("STRIPPED_") && material.isBlock() && (name.endsWith("_LOG") || name.endsWith("_STEM"))) {
                LOG_MATERIALS.add(material);
            }
            if (name.equals("MUSHROOM_STEM")) {
                LOG_MATERIALS.add(material);
            }
            if (name.endsWith("_LEAVES") && material.isBlock()) {
                LEAF_MATERIALS.add(material);
            }
        }

        for (String name : new String[]{
                "MANGROVE_ROOTS", "NETHER_WART_BLOCK", "WARPED_WART_BLOCK",
                "SHROOMLIGHT", "RED_MUSHROOM_BLOCK", "BROWN_MUSHROOM_BLOCK"
        }) {
            addLeafIfPresent(name);
        }

        addDecorationIfPresent("VINE");

        Set<Material> oakLeaves = new HashSet<>();
        oakLeaves.add(Material.OAK_LEAVES);
        addIfPresent(oakLeaves, "AZALEA_LEAVES");
        addIfPresent(oakLeaves, "FLOWERING_AZALEA_LEAVES");
        LEAF_TYPE_CACHE.put(Material.OAK_LOG, Set.copyOf(oakLeaves));
        LEAF_TYPE_CACHE.put(Material.BIRCH_LOG, Set.of(Material.BIRCH_LEAVES));
        LEAF_TYPE_CACHE.put(Material.SPRUCE_LOG, Set.of(Material.SPRUCE_LEAVES));
        LEAF_TYPE_CACHE.put(Material.JUNGLE_LOG, Set.of(Material.JUNGLE_LEAVES));
        LEAF_TYPE_CACHE.put(Material.DARK_OAK_LOG, Set.of(Material.DARK_OAK_LEAVES));
        LEAF_TYPE_CACHE.put(Material.ACACIA_LOG, Set.of(Material.ACACIA_LEAVES));
        putLeafMapping("CHERRY_LOG", "CHERRY_LEAVES");
        putLeafMapping("MANGROVE_LOG", "MANGROVE_LEAVES");
        putLeafMapping("CRIMSON_STEM", "NETHER_WART_BLOCK");
        putLeafMapping("WARPED_STEM", "WARPED_WART_BLOCK");

        Set<Material> mushroomLeaves = new HashSet<>();
        addIfPresent(mushroomLeaves, "RED_MUSHROOM_BLOCK");
        addIfPresent(mushroomLeaves, "BROWN_MUSHROOM_BLOCK");
        addIfPresent(mushroomLeaves, "SHROOMLIGHT");
        Material mushroomStem = Material.getMaterial("MUSHROOM_STEM");
        if (mushroomStem != null && !mushroomLeaves.isEmpty()) {
            LEAF_TYPE_CACHE.put(mushroomStem, Set.copyOf(mushroomLeaves));
        }
    }

    private TreeMaterials() {
    }

    public static boolean isLog(Block block) {
        return isLog(block.getType());
    }

    public static boolean isLog(Material material) {
        return LOG_MATERIALS.contains(material);
    }

    public static boolean isTreeDecoration(Material material) {
        return TREE_DECORATION_MATERIALS.contains(material);
    }

    public static Set<Material> getAssociatedLeafTypes(Material logType) {
        return LEAF_TYPE_CACHE.getOrDefault(logType, LEAF_MATERIALS);
    }

    private static void addLeafIfPresent(String name) {
        Material material = Material.getMaterial(name);
        if (material != null) {
            LEAF_MATERIALS.add(material);
        }
    }

    private static void addDecorationIfPresent(String name) {
        Material material = Material.getMaterial(name);
        if (material != null) {
            TREE_DECORATION_MATERIALS.add(material);
        }
    }

    private static void addIfPresent(Set<Material> materials, String materialName) {
        Material material = Material.getMaterial(materialName);
        if (material != null) {
            materials.add(material);
        }
    }

    private static void putLeafMapping(String logName, String leafName) {
        Material log = Material.getMaterial(logName);
        Material leaf = Material.getMaterial(leafName);
        if (log != null && leaf != null) {
            LEAF_TYPE_CACHE.put(log, Set.of(leaf));
        }
    }
}

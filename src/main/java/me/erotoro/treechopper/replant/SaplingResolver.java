package me.erotoro.treechopper.replant;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class SaplingResolver {

    private static final Map<Material, Material> LOG_TO_SAPLING = buildMapping();

    public Optional<Material> resolve(Material logType) {
        if (logType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOG_TO_SAPLING.get(logType));
    }

    private static Map<Material, Material> buildMapping() {
        EnumMap<Material, Material> mapping = new EnumMap<>(Material.class);
        mapping.put(Material.OAK_LOG, Material.OAK_SAPLING);
        mapping.put(Material.BIRCH_LOG, Material.BIRCH_SAPLING);
        mapping.put(Material.SPRUCE_LOG, Material.SPRUCE_SAPLING);
        mapping.put(Material.JUNGLE_LOG, Material.JUNGLE_SAPLING);
        mapping.put(Material.ACACIA_LOG, Material.ACACIA_SAPLING);
        mapping.put(Material.DARK_OAK_LOG, Material.DARK_OAK_SAPLING);
        addIfPresent(mapping, "CHERRY_LOG", "CHERRY_SAPLING");
        addIfPresent(mapping, "PALE_OAK_LOG", "PALE_OAK_SAPLING");
        addIfPresent(mapping, "MANGROVE_LOG", "MANGROVE_PROPAGULE");
        addIfPresent(mapping, "CRIMSON_STEM", "CRIMSON_FUNGUS");
        addIfPresent(mapping, "WARPED_STEM", "WARPED_FUNGUS");
        return Map.copyOf(mapping);
    }

    private static void addIfPresent(Map<Material, Material> mapping, String logName, String saplingName) {
        Material logType = Material.getMaterial(logName);
        Material saplingType = Material.getMaterial(saplingName);
        if (logType != null && saplingType != null) {
            mapping.put(logType, saplingType);
        }
    }
}

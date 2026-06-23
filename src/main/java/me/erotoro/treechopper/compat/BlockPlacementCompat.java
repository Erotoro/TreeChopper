package me.erotoro.treechopper.compat;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Version-safe access to the block-placement validation APIs used by auto-replant.
 *
 * <ul>
 *   <li>{@link Block#canPlace(BlockData)} — added ~1.19. Absent on 1.16–1.18.</li>
 *   <li>{@code BlockData#isSupported(Block)} — added ~1.19. Absent on 1.16–1.18.</li>
 *   <li>{@code Tag.REPLACEABLE} — added ~1.20.5. Absent on earlier versions.</li>
 * </ul>
 *
 * When a strict check is unavailable the helpers fail open (return {@code true}) so that replanting
 * still works on older servers, while a curated fallback set approximates the REPLACEABLE tag.
 */
public final class BlockPlacementCompat {

    private static final Method CAN_PLACE = resolveMethod(Block.class, "canPlace", BlockData.class);
    private static final Method IS_SUPPORTED = resolveMethod(BlockData.class, "isSupported", Block.class);
    private static final Tag<Material> REPLACEABLE_TAG = resolveReplaceableTag();
    private static final Set<Material> REPLACEABLE_FALLBACK = buildReplaceableFallback();

    private BlockPlacementCompat() {
    }

    /**
     * Whether the planting material may replace the target material. Uses {@code Tag.REPLACEABLE}
     * when present, otherwise a curated fallback set of vanilla replaceable blocks.
     */
    public static boolean isReplaceable(Material material) {
        if (material == null) {
            return false;
        }
        if (REPLACEABLE_TAG != null) {
            return REPLACEABLE_TAG.isTagged(material);
        }
        return REPLACEABLE_FALLBACK.contains(material);
    }

    /**
     * Mirrors {@link Block#canPlace(BlockData)}; returns {@code true} when the method is unavailable.
     */
    public static boolean canPlace(Block block, BlockData data) {
        if (CAN_PLACE == null) {
            return true;
        }
        try {
            return (boolean) CAN_PLACE.invoke(block, data);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return true;
        }
    }

    /**
     * Mirrors {@code BlockData#isSupported(Block)}; returns {@code true} when the method is unavailable.
     */
    public static boolean isSupported(BlockData data, Block block) {
        if (IS_SUPPORTED == null) {
            return true;
        }
        try {
            return (boolean) IS_SUPPORTED.invoke(data, block);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return true;
        }
    }

    private static Method resolveMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Tag<Material> resolveReplaceableTag() {
        // Reading the field forces initialization of the Tag class, which resolves its constants
        // against the server registry. That is fine on a live server but throws (NPE wrapped in
        // ExceptionInInitializerError) when no server is bootstrapped, e.g. in unit tests — hence the
        // broad Throwable catch, which simply falls back to the curated replaceable set.
        try {
            Field field = Tag.class.getField("REPLACEABLE");
            Object value = field.get(null);
            return value instanceof Tag ? (Tag<Material>) value : null;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Set<Material> buildReplaceableFallback() {
        Set<Material> materials = new HashSet<>();
        // Vanilla blocks a sapling can be planted into ("replaceable") prior to the REPLACEABLE tag.
        // Looked up by name so missing materials on a given version are simply skipped. Names differ
        // across versions (e.g. GRASS was renamed SHORT_GRASS in 1.20.3), so both variants are listed.
        for (String name : new String[]{
                "GRASS", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH",
                "SEAGRASS", "TALL_SEAGRASS", "VINE", "SNOW", "FIRE", "STRUCTURE_VOID",
                "LIGHT", "HANGING_ROOTS", "WARPED_ROOTS", "CRIMSON_ROOTS", "NETHER_SPROUTS"
        }) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }
}

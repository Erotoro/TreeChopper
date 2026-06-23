package me.erotoro.treechopper.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Version-safe access to the Unbreaking enchantment.
 *
 * <p>The {@code Enchantment.UNBREAKING} constant only exists on newer API versions (~1.20.6+);
 * on 1.16–1.20.4 the field is named {@code DURABILITY}. Referencing either constant directly would
 * throw {@link NoSuchFieldError} on the versions that lack it. The registry key
 * {@code minecraft:unbreaking}, however, is stable across every supported version, so we resolve the
 * enchantment by key and fall back to the legacy name only if that ever fails.
 */
public final class EnchantmentCompat {

    private static final Enchantment UNBREAKING = resolveUnbreaking();

    private EnchantmentCompat() {
    }

    /**
     * Returns the Unbreaking level on the given item, or {@code 0} if the enchantment could not be
     * resolved on this server (defensive — should not happen on any supported version).
     */
    public static int unbreakingLevel(ItemStack tool) {
        if (tool == null || UNBREAKING == null) {
            return 0;
        }
        return tool.getEnchantmentLevel(UNBREAKING);
    }

    @SuppressWarnings("deprecation") // getByKey/getByName are the only cross-version lookups.
    private static Enchantment resolveUnbreaking() {
        // Both lookups touch the server enchantment registry. That is fine at runtime but throws when
        // no server is bootstrapped (e.g. unit tests); the broad Throwable catch degrades to null,
        // and unbreakingLevel(...) then treats the tool as having no Unbreaking.
        try {
            Enchantment byKey = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (byKey != null) {
                return byKey;
            }
            Enchantment legacy = Enchantment.getByName("DURABILITY");
            if (legacy != null) {
                return legacy;
            }
            return Enchantment.getByName("UNBREAKING");
        } catch (Throwable throwable) {
            return null;
        }
    }
}

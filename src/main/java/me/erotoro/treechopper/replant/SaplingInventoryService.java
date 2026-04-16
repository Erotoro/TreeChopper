package me.erotoro.treechopper.replant;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Objects;

public final class SaplingInventoryService {

    public InventoryPolicyResult verifyAndConsume(PlayerInventory inventory, Material saplingType, int required,
                                                  boolean requireSapling, boolean consumeSapling) {
        if (saplingType == null) {
            return InventoryPolicyResult.fail(ReplantResult.skipped(
                    ReplantResultType.SAPLING_CONSUME_FAILED,
                    "Sapling material is missing."
            ));
        }
        if (inventory == null) {
            return InventoryPolicyResult.fail(ReplantResult.skipped(
                    ReplantResultType.PLAYER_UNAVAILABLE,
                    "Player inventory is unavailable."
            ));
        }
        if (!requireSapling || required <= 0) {
            return InventoryPolicyResult.pass();
        }

        ItemStack[] original = inventory.getContents();
        if (original == null) {
            original = new ItemStack[0];
        }
        int available = countMaterial(original, saplingType);
        if (available < required) {
            return InventoryPolicyResult.fail(ReplantResult.skipped(
                    ReplantResultType.MISSING_SAPLING,
                    "Missing " + saplingType + " x" + required
            ));
        }
        if (!consumeSapling) {
            return InventoryPolicyResult.pass();
        }

        ItemStack[] updated = consumeFromSnapshot(original, saplingType, required);
        if (updated == null) {
            return InventoryPolicyResult.fail(ReplantResult.skipped(
                    ReplantResultType.SAPLING_CONSUME_FAILED,
                    "Failed to consume " + saplingType + " x" + required
            ));
        }
        inventory.setContents(updated);
        return InventoryPolicyResult.pass();
    }

    int countMaterial(ItemStack[] contents, Material material) {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(material, "material");
        int total = 0;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    ItemStack[] consumeFromSnapshot(ItemStack[] contents, Material material, int amount) {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(material, "material");
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }

        int remaining = amount;
        for (int i = 0; i < copy.length && remaining > 0; i++) {
            ItemStack stack = copy[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int taken = Math.min(remaining, stack.getAmount());
            int updatedAmount = stack.getAmount() - taken;
            copy[i] = updatedAmount <= 0 ? null : withAmount(stack, updatedAmount);
            remaining -= taken;
        }
        return remaining == 0 ? copy : null;
    }

    private ItemStack withAmount(ItemStack stack, int amount) {
        ItemStack updated = stack.clone();
        updated.setAmount(amount);
        return updated;
    }

    public record InventoryPolicyResult(boolean allowed, ReplantResult failure) {
        public static InventoryPolicyResult pass() {
            return new InventoryPolicyResult(true, null);
        }

        public static InventoryPolicyResult fail(ReplantResult failure) {
            return new InventoryPolicyResult(false, failure);
        }
    }
}

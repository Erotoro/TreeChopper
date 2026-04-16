package me.erotoro.treechopper.replant;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaplingInventoryServiceTest {

    private final SaplingInventoryService inventoryService = new SaplingInventoryService();

    @Test
    void allowsAndConsumesWhenSaplingIsPresent() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack[] contents = new ItemStack[]{
                mockStack(Material.OAK_SAPLING, 2),
                null
        };
        when(inventory.getContents()).thenReturn(contents);

        SaplingInventoryService.InventoryPolicyResult result = inventoryService.verifyAndConsume(
                inventory, Material.OAK_SAPLING, 1, true, true
        );

        assertTrue(result.allowed());
        verify(inventory).setContents(org.mockito.ArgumentMatchers.any(ItemStack[].class));
    }

    @Test
    void deniesWhenSaplingIsRequiredButAbsent() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack[] contents = new ItemStack[]{mockStack(Material.STONE, 64)};
        when(inventory.getContents()).thenReturn(contents);

        SaplingInventoryService.InventoryPolicyResult result = inventoryService.verifyAndConsume(
                inventory, Material.OAK_SAPLING, 1, true, true
        );

        assertFalse(result.allowed());
        assertNotNull(result.failure());
        assertEquals(ReplantResultType.MISSING_SAPLING, result.failure().type());
        verify(inventory, never()).setContents(org.mockito.ArgumentMatchers.any(ItemStack[].class));
    }

    @Test
    void allowsWhenRequirementIsDisabled() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);

        SaplingInventoryService.InventoryPolicyResult result = inventoryService.verifyAndConsume(
                inventory, Material.OAK_SAPLING, 1, false, true
        );

        assertTrue(result.allowed());
        verify(inventory, never()).setContents(org.mockito.ArgumentMatchers.any(ItemStack[].class));
    }

    @Test
    void allowsWithoutConsumingWhenConsumeDisabled() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack[] contents = new ItemStack[]{mockStack(Material.OAK_SAPLING, 1)};
        when(inventory.getContents()).thenReturn(contents);

        SaplingInventoryService.InventoryPolicyResult result = inventoryService.verifyAndConsume(
                inventory, Material.OAK_SAPLING, 1, true, false
        );

        assertTrue(result.allowed());
        verify(inventory, never()).setContents(org.mockito.ArgumentMatchers.any(ItemStack[].class));
    }

    @Test
    void consumesExactlyRequestedAmountWithoutDuplication() {
        ItemStack[] updated = inventoryService.consumeFromSnapshot(
                new ItemStack[]{
                        mockStack(Material.OAK_SAPLING, 1),
                        mockStack(Material.OAK_SAPLING, 3),
                        mockStack(Material.STONE, 5)
                },
                Material.OAK_SAPLING,
                3
        );

        assertNotNull(updated);
        assertNull(updated[0]);
        assertEquals(1, updated[1].getAmount());
        assertEquals(5, updated[2].getAmount());
    }

    private ItemStack mockStack(Material type, int amount) {
        AtomicInteger mutableAmount = new AtomicInteger(amount);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenAnswer(invocation -> mutableAmount.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            mutableAmount.set(invocation.getArgument(0));
            return null;
        }).when(stack).setAmount(org.mockito.ArgumentMatchers.anyInt());
        when(stack.clone()).thenAnswer(invocation -> mockStack(type, mutableAmount.get()));
        return stack;
    }
}

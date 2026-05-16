package me.erotoro.treechopper.replant;

import me.erotoro.treechopper.coreprotect.CoreProtectService;
import me.erotoro.treechopper.model.BlockKey;
import me.erotoro.treechopper.protection.ProtectionService;
import me.erotoro.treechopper.scheduler.TaskSchedulerFacade;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoReplantServiceTest {

    @Test
    void doesNotConsumeSaplingsWhenProtectionDeniesLaterTarget() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("AutoReplantServiceTest"));

        PluginManager pluginManager = mock(PluginManager.class);
        TaskSchedulerFacade scheduler = mock(TaskSchedulerFacade.class);
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(scheduler).scheduleDelayed(any(Location.class), anyLong(), any(Runnable.class));

        ProtectionService protectionService = mock(ProtectionService.class);
        SaplingResolver saplingResolver = mock(SaplingResolver.class);
        when(saplingResolver.resolve(Material.OAK_LOG)).thenReturn(Optional.of(Material.OAK_SAPLING));

        PlantingValidator plantingValidator = mock(PlantingValidator.class);
        when(plantingValidator.validate(any(Block.class), any(Material.class)))
                .thenReturn(PlantingValidator.ValidationResult.success());

        ReplantLayoutPlanner layoutPlanner = mock(ReplantLayoutPlanner.class);
        World world = mock(World.class);
        UUID worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(world.getName()).thenReturn("world");

        BlockKey targetKeyA = new BlockKey(worldId, 10, 64, 10);
        BlockKey targetKeyB = new BlockKey(worldId, 11, 64, 10);
        when(layoutPlanner.plan(any(Set.class), any(Boolean.class), any(Boolean.class), any(AutoReplantSettings.MegaMode.class)))
                .thenReturn(new ReplantLayout(List.of(targetKeyA, targetKeyB)));

        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[]{
                new org.bukkit.inventory.ItemStack(Material.OAK_SAPLING, 2)
        });

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);

        Chunk loadedChunk = mock(Chunk.class);
        when(loadedChunk.isLoaded()).thenReturn(true);

        Block targetA = mock(Block.class);
        when(targetA.getWorld()).thenReturn(world);
        when(targetA.getChunk()).thenReturn(loadedChunk);
        when(targetA.getX()).thenReturn(10);
        when(targetA.getY()).thenReturn(64);
        when(targetA.getZ()).thenReturn(10);
        when(targetA.getLocation()).thenReturn(new Location(world, 10, 64, 10));

        Block targetB = mock(Block.class);
        when(targetB.getWorld()).thenReturn(world);
        when(targetB.getChunk()).thenReturn(loadedChunk);
        when(targetB.getX()).thenReturn(11);
        when(targetB.getY()).thenReturn(64);
        when(targetB.getZ()).thenReturn(10);
        when(targetB.getLocation()).thenReturn(new Location(world, 11, 64, 10));

        when(world.getBlockAt(10, 64, 10)).thenReturn(targetA);
        when(world.getBlockAt(11, 64, 10)).thenReturn(targetB);

        when(protectionService.canPlace(player, targetA, Material.OAK_SAPLING)).thenReturn(true);
        when(protectionService.canPlace(player, targetB, Material.OAK_SAPLING)).thenReturn(false);

        AutoReplantSettings settings = new AutoReplantSettings(
                true,
                true,
                true,
                0L,
                true,
                AutoReplantSettings.MegaMode.FOUR_SAPLINGS,
                false,
                false,
                Set.of(),
                false
        );

        AutoReplantService service = new AutoReplantService(
                plugin,
                pluginManager,
                scheduler,
                settings,
                protectionService,
                saplingResolver,
                plantingValidator,
                layoutPlanner,
                new SaplingInventoryService(),
                createCoreProtectService()
        );

        Location anchor = new Location(world, 10, 64, 10);
        ReplantRequest request = new ReplantRequest(
                player,
                anchor,
                Material.OAK_LOG,
                Set.of(targetKeyA, targetKeyB),
                false,
                true
        );

        service.scheduleReplant(request);

        verify(inventory, never()).setContents(any(org.bukkit.inventory.ItemStack[].class));
        verify(targetA, never()).setType(Material.OAK_SAPLING, false);
        verify(targetB, never()).setType(Material.OAK_SAPLING, false);
    }

    private static CoreProtectService createCoreProtectService() {
        try {
            Constructor<CoreProtectService> constructor = CoreProtectService.class
                    .getDeclaredConstructor(Optional.class, Logger.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Optional.empty(), Logger.getLogger("AutoReplantServiceTest"), false);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to create CoreProtectService test instance", exception);
        }
    }
}

package me.erotoro.treechopper.tree;

import me.erotoro.treechopper.TreeChopperSettings;
import me.erotoro.treechopper.coreprotect.CoreProtectService;
import me.erotoro.treechopper.scheduler.TaskSchedulerFacade;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoliageBreakServiceTest {

    private static final int[][] NEIGHBOR_OFFSETS = createNeighborOffsets();

    @Test
    void breaksCocoaPodsAttachedToJungleTrees() {
        TestBlockWorld world = new TestBlockWorld();
        world.setType(0, 64, 0, Material.JUNGLE_LOG);
        world.setType(1, 64, 0, Material.COCOA);

        TaskSchedulerFacade scheduler = createInlineScheduler();

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("tester");

        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.isDropItems()).thenReturn(true);

        FoliageBreakService service = new FoliageBreakService(
                TreeChopperSettings.DEFAULT,
                scheduler,
                NEIGHBOR_OFFSETS,
                (testPlayer, block) -> event,
                createCoreProtectService()
        );

        service.breakFoliage(
                player,
                Set.of(new Location(world.world(), 0, 64, 0)),
                Set.of(world.blockAt(0, 64, 0)),
                Material.JUNGLE_LOG
        );

        assertEquals(Material.AIR, world.getType(1, 64, 0));
    }

    @Test
    void breaksPaleHangingMossWhenAvailable() {
        Material paleOakLog = Material.getMaterial("PALE_OAK_LOG");
        Material paleOakLeaves = Material.getMaterial("PALE_OAK_LEAVES");
        Material paleHangingMoss = Material.getMaterial("PALE_HANGING_MOSS");
        if (paleOakLog == null || paleOakLeaves == null || paleHangingMoss == null) {
            return;
        }

        TestBlockWorld world = new TestBlockWorld();
        world.setType(0, 64, 0, paleOakLog);
        world.setType(1, 65, 0, paleOakLeaves);
        world.setType(1, 64, 0, paleHangingMoss);

        FoliageBreakService service = createService();

        service.breakFoliage(
                createPlayer(),
                Set.of(new Location(world.world(), 0, 64, 0)),
                Set.of(world.blockAt(0, 64, 0)),
                paleOakLog
        );

        assertEquals(Material.AIR, world.getType(1, 64, 0));
    }

    @Test
    void breaksMangroveRootsAndPropagulesWhenAvailable() {
        Material mangroveLog = Material.getMaterial("MANGROVE_LOG");
        Material mangroveLeaves = Material.getMaterial("MANGROVE_LEAVES");
        Material mangroveRoots = Material.getMaterial("MANGROVE_ROOTS");
        Material muddyMangroveRoots = Material.getMaterial("MUDDY_MANGROVE_ROOTS");
        Material mangrovePropagule = Material.getMaterial("MANGROVE_PROPAGULE");
        if (mangroveLog == null || mangroveLeaves == null || mangroveRoots == null || muddyMangroveRoots == null || mangrovePropagule == null) {
            return;
        }

        TestBlockWorld world = new TestBlockWorld();
        world.setType(0, 64, 0, mangroveLog);
        world.setType(0, 65, 0, mangroveLeaves);
        world.setType(0, 65, 1, mangroveLeaves);
        world.setType(0, 63, 0, mangroveRoots);
        world.setType(1, 63, 0, muddyMangroveRoots);
        world.setType(0, 63, 1, mangroveRoots);
        world.setType(0, 64, 1, mangrovePropagule);

        FoliageBreakService service = createService();

        service.breakFoliage(
                createPlayer(),
                Set.of(new Location(world.world(), 0, 64, 0)),
                Set.of(world.blockAt(0, 64, 0)),
                mangroveLog
        );

        assertEquals(Material.AIR, world.getType(0, 63, 0));
        assertEquals(Material.AIR, world.getType(1, 63, 0));
        assertEquals(Material.AIR, world.getType(0, 64, 1));
    }

    @Test
    void doesNotBreakRootsOwnedByNeighboringMangroveTree() {
        Material mangroveLog = Material.getMaterial("MANGROVE_LOG");
        Material mangroveLeaves = Material.getMaterial("MANGROVE_LEAVES");
        Material mangroveRoots = Material.getMaterial("MANGROVE_ROOTS");
        if (mangroveLog == null || mangroveLeaves == null || mangroveRoots == null) {
            return;
        }

        TestBlockWorld world = new TestBlockWorld();
        world.setType(0, 64, 0, mangroveLog);
        world.setType(0, 65, 0, mangroveLeaves);
        world.setType(3, 64, 0, mangroveLog);
        world.setType(3, 65, 0, mangroveLeaves);

        world.setType(0, 63, 0, mangroveRoots);
        world.setType(1, 63, 0, mangroveRoots);
        world.setType(2, 63, 0, mangroveRoots);
        world.setType(3, 63, 0, mangroveRoots);

        FoliageBreakService service = createService();

        service.breakFoliage(
                createPlayer(),
                Set.of(new Location(world.world(), 0, 64, 0)),
                Set.of(world.blockAt(0, 64, 0)),
                mangroveLog
        );

        assertEquals(Material.AIR, world.getType(0, 63, 0));
        assertEquals(Material.AIR, world.getType(1, 63, 0));
        assertEquals(mangroveRoots, world.getType(2, 63, 0));
        assertEquals(mangroveRoots, world.getType(3, 63, 0));
    }

    @Test
    void doesNotBreakVinesOwnedByNeighboringTree() {
        TestBlockWorld world = new TestBlockWorld();
        world.setType(0, 64, 0, Material.OAK_LOG);
        world.setType(0, 65, 0, Material.OAK_LEAVES);
        world.setType(5, 64, 0, Material.OAK_LOG);
        world.setType(5, 65, 0, Material.OAK_LEAVES);

        world.setType(1, 64, 0, Material.VINE);
        world.setType(2, 64, 0, Material.VINE);
        world.setType(3, 64, 0, Material.VINE);
        world.setType(4, 64, 0, Material.VINE);

        FoliageBreakService service = createService();

        service.breakFoliage(
                createPlayer(),
                Set.of(new Location(world.world(), 0, 64, 0)),
                Set.of(world.blockAt(0, 64, 0)),
                Material.OAK_LOG
        );

        assertEquals(Material.AIR, world.getType(1, 64, 0));
        assertEquals(Material.AIR, world.getType(2, 64, 0));
        assertEquals(Material.VINE, world.getType(3, 64, 0));
        assertEquals(Material.VINE, world.getType(4, 64, 0));
    }

    @Test
    void breaksLeavesOverflowingIntoChunksWithoutLogs() {
        // Log sits at the corner of chunk (0,0); the canopy spills into chunks
        // (1,0), (0,1), (1,1) where there are no logs. Regression for the bug where
        // per-chunk dispatch skipped chunks with no logs and left mega-tree canopy
        // hanging in the air.
        TestBlockWorld world = new TestBlockWorld();
        world.setType(15, 64, 15, Material.JUNGLE_LOG);
        world.setType(16, 64, 16, Material.JUNGLE_LEAVES); // chunk (1, 1) — no logs here
        world.setType(17, 64, 17, Material.JUNGLE_LEAVES); // reached via BFS within chunk (1, 1)
        world.setType(16, 64, 14, Material.JUNGLE_LEAVES); // chunk (1, 0)
        world.setType(14, 64, 16, Material.JUNGLE_LEAVES); // chunk (0, 1)

        FoliageBreakService service = createService();

        service.breakFoliage(
                createPlayer(),
                Set.of(new Location(world.world(), 15, 64, 15)),
                Set.of(world.blockAt(15, 64, 15)),
                Material.JUNGLE_LOG
        );

        assertEquals(Material.AIR, world.getType(16, 64, 16));
        assertEquals(Material.AIR, world.getType(17, 64, 17));
        assertEquals(Material.AIR, world.getType(16, 64, 14));
        assertEquals(Material.AIR, world.getType(14, 64, 16));
    }

    private static int[][] createNeighborOffsets() {
        int[][] offsets = new int[26][3];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    offsets[index++] = new int[]{dx, dy, dz};
                }
            }
        }
        return offsets;
    }

    private static CoreProtectService createCoreProtectService() {
        try {
            Constructor<CoreProtectService> constructor = CoreProtectService.class
                    .getDeclaredConstructor(Optional.class, Logger.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Optional.empty(), Logger.getLogger("FoliageBreakServiceTest"), false);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to create CoreProtectService test instance", exception);
        }
    }

    private static FoliageBreakService createService() {
        TaskSchedulerFacade scheduler = createInlineScheduler();

        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.isDropItems()).thenReturn(true);

        return new FoliageBreakService(
                TreeChopperSettings.DEFAULT,
                scheduler,
                NEIGHBOR_OFFSETS,
                (testPlayer, block) -> event,
                createCoreProtectService()
        );
    }

    private static TaskSchedulerFacade createInlineScheduler() {
        TaskSchedulerFacade scheduler = mock(TaskSchedulerFacade.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<List<Block>> action = invocation.getArgument(3);
            action.accept(invocation.getArgument(0));
            return null;
        }).when(scheduler).scheduleBlockBatches(anyCollection(), anyLong(), anyInt(), any());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(scheduler).scheduleDelayed(any(Location.class), anyLong(), any(Runnable.class));
        return scheduler;
    }

    private static Player createPlayer() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("tester");
        return player;
    }
}

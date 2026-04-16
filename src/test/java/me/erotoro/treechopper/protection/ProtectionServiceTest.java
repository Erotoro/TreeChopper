package me.erotoro.treechopper.protection;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtectionServiceTest {

    @Test
    void breakChecksDoNotDependOnPlacementChecks() {
        AtomicInteger breakChecks = new AtomicInteger();
        AtomicInteger placeChecks = new AtomicInteger();

        ProtectionHook hook = new ProtectionHook() {
            @Override
            public String getName() {
                return "test";
            }

            @Override
            public boolean canBreak(Player player, Block block) {
                breakChecks.incrementAndGet();
                return true;
            }

            @Override
            public boolean canPlace(Player player, Block block, Material material) {
                placeChecks.incrementAndGet();
                return false;
            }
        };

        ProtectionService service = new ProtectionService(
                ProtectionSettings.DEFAULT,
                List.of(hook),
                Logger.getLogger("ProtectionServiceTest")
        );

        Player player = mock(Player.class);
        Block block = mock(Block.class);
        World world = mock(World.class);
        when(block.getWorld()).thenReturn(world);

        assertTrue(service.canBreakAll(player, List.of(block)));
        assertFalse(service.canPlace(player, block, Material.OAK_SAPLING));
        assertTrue(breakChecks.get() > 0);
        assertTrue(placeChecks.get() > 0);
    }

    @Test
    void explicitBreakDenyStillCancelsBreakAll() {
        ProtectionHook hook = new ProtectionHook() {
            @Override
            public String getName() {
                return "deny-break";
            }

            @Override
            public boolean canBreak(Player player, Block block) {
                return false;
            }

            @Override
            public boolean canPlace(Player player, Block block, Material material) {
                return true;
            }
        };

        ProtectionService service = new ProtectionService(
                ProtectionSettings.DEFAULT,
                List.of(hook),
                Logger.getLogger("ProtectionServiceTest")
        );

        Player player = mock(Player.class);
        Block block = mock(Block.class);
        World world = mock(World.class);
        when(block.getWorld()).thenReturn(world);

        assertFalse(service.canBreakAll(player, List.of(block)));
    }

    @Test
    void hookExceptionIsNonApplicableAndDoesNotHardDeny() {
        ProtectionHook hook = new ProtectionHook() {
            @Override
            public String getName() {
                return "throws";
            }

            @Override
            public boolean canBreak(Player player, Block block) {
                throw new IllegalStateException("boom");
            }

            @Override
            public boolean canPlace(Player player, Block block, Material material) {
                throw new IllegalStateException("boom");
            }
        };

        ProtectionService service = new ProtectionService(
                ProtectionSettings.DEFAULT,
                List.of(hook),
                Logger.getLogger("ProtectionServiceTest")
        );

        Player player = mock(Player.class);
        Block block = mock(Block.class);
        World world = mock(World.class);
        when(block.getWorld()).thenReturn(world);

        assertTrue(service.canBreakAll(player, List.of(block)));
        assertTrue(service.canPlace(player, block, Material.OAK_SAPLING));
    }
}

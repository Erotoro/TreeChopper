package me.erotoro.treechopper.replant;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaplingResolverTest {

    private final SaplingResolver resolver = new SaplingResolver();

    @Test
    void resolvesRegularLogsToSaplings() {
        assertEquals(Material.OAK_SAPLING, resolver.resolve(Material.OAK_LOG).orElseThrow());
        assertEquals(Material.DARK_OAK_SAPLING, resolver.resolve(Material.DARK_OAK_LOG).orElseThrow());
    }

    @Test
    void resolvesMangroveToPropaguleWhenAvailable() {
        Material mangroveLog = Material.getMaterial("MANGROVE_LOG");
        Material mangrovePropagule = Material.getMaterial("MANGROVE_PROPAGULE");
        if (mangroveLog == null || mangrovePropagule == null) {
            return;
        }
        assertEquals(mangrovePropagule, resolver.resolve(mangroveLog).orElseThrow());
    }

    @Test
    void returnsEmptyForUnsupportedTreeTypes() {
        assertTrue(resolver.resolve(Material.MUSHROOM_STEM).isEmpty());
        assertTrue(resolver.resolve(null).isEmpty());
    }
}

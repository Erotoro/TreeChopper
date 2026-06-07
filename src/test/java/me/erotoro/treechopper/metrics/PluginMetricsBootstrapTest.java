package me.erotoro.treechopper.metrics;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PluginMetricsBootstrapTest {

    @Test
    void initializesBStatsUsingRegisteredPluginId() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        AtomicReference<JavaPlugin> capturedPlugin = new AtomicReference<>();
        AtomicInteger capturedPluginId = new AtomicInteger(-1);
        BiFunction<JavaPlugin, Integer, Object> metricsFactory = (captured, pluginId) -> {
            capturedPlugin.set(captured);
            capturedPluginId.set(pluginId);
            return new Object();
        };

        PluginMetricsBootstrap bootstrap = new PluginMetricsBootstrap(metricsFactory);
        bootstrap.initialize(plugin);

        assertEquals(plugin, capturedPlugin.get());
        assertEquals(31348, capturedPluginId.get());
    }
}

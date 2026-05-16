package me.erotoro.treechopper.metrics;

import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import java.util.Objects;
import java.util.function.BiFunction;

public final class PluginMetricsBootstrap {

    static final int BSTATS_PLUGIN_ID = 27579;

    private final BiFunction<JavaPlugin, Integer, Object> metricsFactory;

    public PluginMetricsBootstrap() {
        this((plugin, pluginId) -> new Metrics(plugin, pluginId));
    }

    PluginMetricsBootstrap(BiFunction<JavaPlugin, Integer, Object> metricsFactory) {
        this.metricsFactory = Objects.requireNonNull(metricsFactory, "metricsFactory");
    }

    public void initialize(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        metricsFactory.apply(plugin, BSTATS_PLUGIN_ID);
    }
}

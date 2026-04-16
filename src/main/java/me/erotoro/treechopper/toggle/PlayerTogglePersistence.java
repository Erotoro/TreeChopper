package me.erotoro.treechopper.toggle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class PlayerTogglePersistence {

    private final File file;

    public PlayerTogglePersistence(File file) {
        this.file = file;
    }

    public Map<UUID, Boolean> load() {
        Map<UUID, Boolean> loaded = new HashMap<>();
        if (file == null || !file.exists()) {
            return loaded;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = configuration.getConfigurationSection("players");
        if (playersSection == null) {
            return loaded;
        }

        for (String uuidKey : playersSection.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            Object value = playersSection.get(uuidKey);
            if (value instanceof Boolean enabled) {
                loaded.put(playerId, enabled);
            }
        }

        return loaded;
    }

    public void save(Map<UUID, Boolean> playerStates) throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection section = configuration.createSection("players");

        TreeMap<String, Boolean> sorted = new TreeMap<>();
        for (Map.Entry<UUID, Boolean> entry : playerStates.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            sorted.put(entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<String, Boolean> entry : sorted.entrySet()) {
            section.set(entry.getKey(), entry.getValue());
        }

        saveAtomically(configuration);
    }

    private void saveAtomically(YamlConfiguration configuration) throws IOException {
        Path target = file.toPath();
        Path parent = target.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        configuration.save(temp.toFile());

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

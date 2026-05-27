package me.erotoro.treechopper.toggle;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class PlayerToggleService {

    private final PlayerTogglePersistence persistence;
    private final Logger logger;

    // Reference is reassigned on load() to atomically swap the entire state.
    // Region threads (Folia) read isEnabled(); main thread writes during reload.
    private volatile Map<UUID, Boolean> playerStates = new ConcurrentHashMap<>();
    private volatile PlayerToggleSettings settings;
    private volatile boolean dirty;

    public PlayerToggleService(PlayerTogglePersistence persistence, PlayerToggleSettings settings, Logger logger) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.settings = settings == null ? PlayerToggleSettings.DEFAULT : settings;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean isFeatureEnabled() {
        return settings.enabled();
    }

    public boolean isEnabled(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerToggleSettings currentSettings = settings;
        if (!currentSettings.enabled()) {
            return true;
        }
        return playerStates.getOrDefault(playerId, currentSettings.defaultEnabled());
    }

    public boolean toggle(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerToggleSettings currentSettings = settings;
        if (!currentSettings.enabled()) {
            return true;
        }
        boolean defaultEnabled = currentSettings.defaultEnabled();
        Boolean newState = playerStates.compute(playerId, (id, existing) -> {
            boolean previous = existing != null ? existing : defaultEnabled;
            return !previous;
        });
        dirty = true;
        if (currentSettings.saveOnChange()) {
            save();
        }
        return newState != null && newState;
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerToggleSettings currentSettings = settings;
        if (!currentSettings.enabled()) {
            return;
        }
        Boolean previous = playerStates.put(playerId, enabled);
        if (!Objects.equals(previous, enabled)) {
            dirty = true;
            if (currentSettings.saveOnChange()) {
                save();
            }
        }
    }

    public void load() {
        Map<UUID, Boolean> loaded = persistence.load();
        // Atomic swap: readers see either old or new map, never a torn intermediate state.
        playerStates = new ConcurrentHashMap<>(loaded);
        dirty = false;
    }

    public void save() {
        try {
            // Snapshot prevents the persistence layer from observing concurrent modifications.
            persistence.save(new HashMap<>(playerStates));
            dirty = false;
        } catch (IOException exception) {
            logger.warning("Failed to save player toggles: " + exception.getMessage());
        }
    }

    public void updateSettings(PlayerToggleSettings settings) {
        this.settings = settings == null ? PlayerToggleSettings.DEFAULT : settings;
    }

    public boolean isDirty() {
        return dirty;
    }

    public int size() {
        return playerStates.size();
    }
}

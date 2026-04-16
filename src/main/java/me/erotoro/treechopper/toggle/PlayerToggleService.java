package me.erotoro.treechopper.toggle;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public final class PlayerToggleService {

    private final PlayerTogglePersistence persistence;
    private final Logger logger;
    private final Map<UUID, Boolean> playerStates = new HashMap<>();
    private PlayerToggleSettings settings;
    private boolean dirty;

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
        if (!settings.enabled()) {
            return true;
        }
        return playerStates.getOrDefault(playerId, settings.defaultEnabled());
    }

    public boolean toggle(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!settings.enabled()) {
            return true;
        }
        boolean newState = !isEnabled(playerId);
        setEnabled(playerId, newState);
        return newState;
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        if (!settings.enabled()) {
            return;
        }
        Boolean previous = playerStates.put(playerId, enabled);
        if (!Objects.equals(previous, enabled)) {
            dirty = true;
            if (settings.saveOnChange()) {
                save();
            }
        }
    }

    public void load() {
        playerStates.clear();
        playerStates.putAll(persistence.load());
        dirty = false;
    }

    public void save() {
        try {
            persistence.save(playerStates);
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

package me.erotoro.treechopper.toggle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerToggleServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void usesDefaultStateWhenNoExplicitEntryExists() {
        PlayerToggleService service = new PlayerToggleService(
                new PlayerTogglePersistence(new File("unused.yml")),
                new PlayerToggleSettings(true, true, true),
                Logger.getLogger("test")
        );

        assertTrue(service.isEnabled(UUID.randomUUID()));
    }

    @Test
    void togglesStateAndTracksDirtyWhenImmediateSaveIsDisabled() {
        PlayerToggleService service = new PlayerToggleService(
                new PlayerTogglePersistence(new File("unused.yml")),
                new PlayerToggleSettings(true, true, false),
                Logger.getLogger("test")
        );
        UUID playerId = UUID.randomUUID();

        boolean stateAfterFirstToggle = service.toggle(playerId);
        boolean stateAfterSecondToggle = service.toggle(playerId);

        assertFalse(stateAfterFirstToggle);
        assertTrue(stateAfterSecondToggle);
        assertTrue(service.isDirty());
    }

    @Test
    void savesImmediatelyWhenConfiguredToSaveOnChange() {
        File file = tempDir.resolve("player-toggles.yml").toFile();
        PlayerToggleService service = new PlayerToggleService(
                new PlayerTogglePersistence(file),
                new PlayerToggleSettings(true, true, true),
                Logger.getLogger("test")
        );

        service.toggle(UUID.randomUUID());

        assertFalse(service.isDirty());
        assertTrue(file.exists());
    }

    @Test
    void featureDisabledBypassesPerPlayerState() {
        PlayerToggleService service = new PlayerToggleService(
                new PlayerTogglePersistence(new File("unused.yml")),
                new PlayerToggleSettings(false, false, true),
                Logger.getLogger("test")
        );
        UUID playerId = UUID.randomUUID();

        assertTrue(service.isEnabled(playerId));
        boolean toggled = service.toggle(playerId);

        assertTrue(toggled);
        assertFalse(service.isDirty());
    }

    @Test
    void appliesLoadedStateAndUsesDefaultForMissingPlayers() {
        InMemoryPlayerTogglePersistence persistence = new InMemoryPlayerTogglePersistence();
        UUID enabledPlayer = UUID.randomUUID();
        UUID disabledPlayer = UUID.randomUUID();
        persistence.stored = Map.of(
                enabledPlayer, true,
                disabledPlayer, false
        );

        PlayerToggleService service = new PlayerToggleService(
                persistence,
                new PlayerToggleSettings(true, false, true),
                Logger.getLogger("test")
        );
        service.load();

        assertTrue(service.isEnabled(enabledPlayer));
        assertFalse(service.isEnabled(disabledPlayer));
        assertFalse(service.isEnabled(UUID.randomUUID()));
    }

    private static final class InMemoryPlayerTogglePersistence extends PlayerTogglePersistence {
        private Map<UUID, Boolean> stored = Map.of();

        private InMemoryPlayerTogglePersistence() {
            super(new File("unused.yml"));
        }

        @Override
        public Map<UUID, Boolean> load() {
            return stored;
        }
    }
}

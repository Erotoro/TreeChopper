package me.erotoro.treechopper.toggle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void survivesConcurrentTogglesFromMultipleThreads() throws InterruptedException {
        PlayerToggleService service = new PlayerToggleService(
                new PlayerTogglePersistence(new File("unused.yml")),
                new PlayerToggleSettings(true, true, false),
                Logger.getLogger("test")
        );

        int playerCount = 64;
        int togglesPerPlayer = 100;
        UUID[] players = new UUID[playerCount];
        for (int i = 0; i < playerCount; i++) {
            players[i] = UUID.randomUUID();
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(playerCount);

        for (UUID playerId : players) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < togglesPerPlayer; i++) {
                        service.toggle(playerId);
                        service.isEnabled(playerId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS), "Concurrent toggles should complete within timeout");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Even number of toggles per player → final state matches default-enabled (true).
        assertEquals(playerCount, service.size());
        for (UUID playerId : players) {
            assertTrue(service.isEnabled(playerId),
                    "After even toggles each player should be back to the default state");
        }
    }

    @Test
    void loadAtomicallySwapsStateWithoutVisibleEmptyWindow() {
        PlayerToggleService service = new PlayerToggleService(
                new PlayerTogglePersistence(new File("unused.yml")),
                new PlayerToggleSettings(true, false, false),
                Logger.getLogger("test")
        );

        UUID existingPlayer = UUID.randomUUID();
        service.setEnabled(existingPlayer, true);
        assertTrue(service.isEnabled(existingPlayer));

        InMemoryPlayerTogglePersistence replacement = new InMemoryPlayerTogglePersistence();
        UUID loadedPlayer = UUID.randomUUID();
        replacement.stored = Map.of(loadedPlayer, true);

        PlayerToggleService swapped = new PlayerToggleService(
                replacement,
                new PlayerToggleSettings(true, false, false),
                Logger.getLogger("test")
        );
        swapped.load();
        assertTrue(swapped.isEnabled(loadedPlayer));
        // Previously enabled players (default disabled here) should now read the default.
        assertFalse(swapped.isEnabled(existingPlayer));
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

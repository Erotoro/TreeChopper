package me.erotoro.treechopper.toggle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTogglePersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsPlayerToggleStates() throws IOException {
        File file = tempDir.resolve("player-toggles.yml").toFile();
        PlayerTogglePersistence persistence = new PlayerTogglePersistence(file);
        UUID enabledPlayer = UUID.randomUUID();
        UUID disabledPlayer = UUID.randomUUID();

        persistence.save(Map.of(
                enabledPlayer, true,
                disabledPlayer, false
        ));

        Map<UUID, Boolean> loaded = persistence.load();

        assertEquals(2, loaded.size());
        assertTrue(loaded.get(enabledPlayer));
        assertFalse(loaded.get(disabledPlayer));
    }

    @Test
    void skipsMalformedEntriesWithoutThrowing() throws IOException {
        Path filePath = tempDir.resolve("player-toggles.yml");
        Files.writeString(filePath, """
                players:
                  not-a-uuid: true
                  "e6962da5-50ca-4bc3-8af7-f1305d0f4d1c": invalid
                  "a4e73078-18e6-4ae4-8012-d987b2fd0f0a": false
                """);
        PlayerTogglePersistence persistence = new PlayerTogglePersistence(filePath.toFile());

        Map<UUID, Boolean> loaded = persistence.load();

        assertEquals(1, loaded.size());
        assertFalse(loaded.get(UUID.fromString("a4e73078-18e6-4ae4-8012-d987b2fd0f0a")));
    }

    @Test
    void missingFileLoadsAsEmptyMap() {
        File file = tempDir.resolve("missing-player-toggles.yml").toFile();
        PlayerTogglePersistence persistence = new PlayerTogglePersistence(file);

        Map<UUID, Boolean> loaded = persistence.load();

        assertTrue(loaded.isEmpty());
    }
}

package me.erotoro.treechopper.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalizationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void getFallsBackToFallbackLanguageWhenKeyMissingInSelectedLanguage() throws IOException {
        JavaPlugin plugin = mockPlugin(tempDir.toFile());
        prepareLanguageFiles(
                tempDir,
                """
                messages:
                  test:
                    key: "English value"
                """,
                """
                messages:
                  test:
                    another-key: "Russian value"
                """,
                """
                messages:
                  test:
                    key: "Ukrainian value"
                """
        );

        LocalizationService service = new LocalizationService(plugin);
        YamlConfiguration config = new YamlConfiguration();
        config.set("language.default", "ru");
        config.set("language.fallback", "en");
        service.load(config);

        assertEquals("English value", service.get("messages.test.key"));
    }

    @Test
    void getReturnsKeyWhenMissingInSelectedAndFallback() throws IOException {
        JavaPlugin plugin = mockPlugin(tempDir.toFile());
        prepareLanguageFiles(tempDir, "messages: {}", "messages: {}", "messages: {}");

        LocalizationService service = new LocalizationService(plugin);
        YamlConfiguration config = new YamlConfiguration();
        config.set("language.default", "uk");
        config.set("language.fallback", "en");
        service.load(config);

        assertEquals("messages.unknown.key", service.get("messages.unknown.key"));
    }

    private JavaPlugin mockPlugin(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LocalizationServiceTest"));
        when(plugin.getResource(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        return plugin;
    }

    private void prepareLanguageFiles(Path root, String enYaml, String ruYaml, String ukYaml) throws IOException {
        Path langDir = root.resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("en.yml"), enYaml);
        Files.writeString(langDir.resolve("ru.yml"), ruYaml);
        Files.writeString(langDir.resolve("uk.yml"), ukYaml);
    }
}

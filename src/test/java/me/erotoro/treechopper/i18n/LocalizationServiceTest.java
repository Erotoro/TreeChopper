package me.erotoro.treechopper.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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

    @Test
    void loadSupportsAdditionalBundledLanguages() throws IOException {
        JavaPlugin plugin = mockPlugin(tempDir.toFile());
        prepareLanguageFiles(
                tempDir,
                Map.ofEntries(
                        Map.entry("en.yml", """
                                messages:
                                  test:
                                    key: "English value"
                                """),
                        Map.entry("ru.yml", "messages: {}"),
                        Map.entry("uk.yml", "messages: {}"),
                        Map.entry("pl.yml", """
                                messages:
                                  test:
                                    key: "Polska wartosc"
                                """),
                        Map.entry("de.yml", """
                                messages:
                                  test:
                                    key: "Deutscher Wert"
                                """),
                        Map.entry("fr.yml", """
                                messages:
                                  test:
                                    key: "Valeur francaise"
                                """),
                        Map.entry("es.yml", """
                                messages:
                                  test:
                                    key: "Valor espanol"
                                """),
                        Map.entry("it.yml", """
                                messages:
                                  test:
                                    key: "Valore italiano"
                                """),
                        Map.entry("cs.yml", """
                                messages:
                                  test:
                                    key: "Ceska hodnota"
                                """)
                )
        );

        LocalizationService service = new LocalizationService(plugin);
        YamlConfiguration config = new YamlConfiguration();
        config.set("language.default", "pl");
        config.set("language.fallback", "cs");
        service.load(config);

        assertEquals("Polska wartosc", service.get("messages.test.key"));
    }

    private JavaPlugin mockPlugin(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LocalizationServiceTest"));
        when(plugin.getResource(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        return plugin;
    }

    private void prepareLanguageFiles(Path root, String enYaml, String ruYaml, String ukYaml) throws IOException {
        prepareLanguageFiles(root, Map.of(
                "en.yml", enYaml,
                "ru.yml", ruYaml,
                "uk.yml", ukYaml
        ));
    }

    private void prepareLanguageFiles(Path root, Map<String, String> files) throws IOException {
        Path langDir = root.resolve("lang");
        Files.createDirectories(langDir);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Files.writeString(langDir.resolve(entry.getKey()), entry.getValue());
        }
    }
}

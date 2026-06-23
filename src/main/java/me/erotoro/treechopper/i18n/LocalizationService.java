package me.erotoro.treechopper.i18n;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

public final class LocalizationService {

    private static final String DEFAULT_LANGUAGE_CODE = "en";
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "en", "ru", "uk", "pl", "de", "fr", "es", "it", "cs"
    );

    private final JavaPlugin plugin;
    private final Logger logger;
    private final File langDirectory;

    private volatile Map<String, Map<String, String>> messagesByLanguage = Map.of();
    private volatile String selectedLanguage = DEFAULT_LANGUAGE_CODE;
    private volatile String fallbackLanguage = DEFAULT_LANGUAGE_CODE;

    public LocalizationService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(plugin.getLogger(), "plugin logger");
        this.langDirectory = new File(plugin.getDataFolder(), "lang");
    }

    public void load(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        ensureLanguageDirectory();
        for (String languageCode : SUPPORTED_LANGUAGES) {
            ensureLanguageFileExists(languageCode);
        }

        String configuredDefault = normalizeLanguageCode(config.getString("language.default", DEFAULT_LANGUAGE_CODE));
        String configuredFallback = normalizeLanguageCode(config.getString("language.fallback", DEFAULT_LANGUAGE_CODE));
        String resolvedFallback = resolveConfiguredLanguage(configuredFallback, DEFAULT_LANGUAGE_CODE, "fallback");
        String resolvedDefault = resolveConfiguredLanguage(configuredDefault, resolvedFallback, "default");

        Map<String, Map<String, String>> loaded = new HashMap<>();
        for (String code : SUPPORTED_LANGUAGES) {
            loaded.put(code, Collections.unmodifiableMap(loadSingleLanguage(code)));
        }

        messagesByLanguage = Collections.unmodifiableMap(loaded);
        fallbackLanguage = resolvedFallback;
        selectedLanguage = resolvedDefault;
    }

    public String get(String key) {
        String resolvedKey = Objects.requireNonNullElse(key, "");
        if (resolvedKey.isBlank()) {
            return resolvedKey;
        }

        String localized = findMessage(selectedLanguage, resolvedKey);
        if (localized != null) {
            return applyFormatting(localized);
        }

        if (!selectedLanguage.equals(fallbackLanguage)) {
            localized = findMessage(fallbackLanguage, resolvedKey);
            if (localized != null) {
                return applyFormatting(localized);
            }
        }

        return resolvedKey;
    }

    public String get(Player player, String key) {
        return get(key);
    }

    private String findMessage(String language, String key) {
        Map<String, String> languageMessages = messagesByLanguage.get(language);
        if (languageMessages == null) {
            return null;
        }
        return languageMessages.get(key);
    }

    private String applyFormatting(String message) {
        if (message.indexOf('&') < 0) {
            return message;
        }

        StringBuilder builder = new StringBuilder(message.length());
        for (int index = 0; index < message.length(); index++) {
            char current = message.charAt(index);
            if (current == '&' && index + 1 < message.length()) {
                char next = message.charAt(index + 1);
                if (isLegacyColorCode(next)) {
                    builder.append('\u00A7').append(Character.toLowerCase(next));
                    index++;
                    continue;
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private void ensureLanguageDirectory() {
        if (!langDirectory.exists() && !langDirectory.mkdirs()) {
            logger.warning("Failed to create language directory: " + langDirectory.getAbsolutePath());
        }
    }

    private void ensureLanguageFileExists(String languageCode) {
        File target = languageFile(languageCode);
        if (target.exists()) {
            return;
        }

        String resourcePath = "lang/" + languageCode + ".yml";
        if (plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
            return;
        }

        if (DEFAULT_LANGUAGE_CODE.equals(languageCode)) {
            YamlConfiguration config = new YamlConfiguration();
            config.set("messages.missing-default-file", "&cMissing default language file.");
            try {
                config.save(target);
            } catch (IOException exception) {
                logger.warning("Failed to create default language file: " + exception.getMessage());
            }
        }
    }

    private Map<String, String> loadSingleLanguage(String languageCode) {
        File file = languageFile(languageCode);
        YamlConfiguration configuration = new YamlConfiguration();

        try {
            configuration.load(file);
        } catch (InvalidConfigurationException exception) {
            logger.warning("Invalid language YAML for '" + languageCode + "': " + exception.getMessage());
            return Map.of();
        } catch (IOException exception) {
            logger.warning("Failed to load language '" + languageCode + "': " + exception.getMessage());
            return Map.of();
        }

        Map<String, String> flattened = new HashMap<>();
        flattenSection(configuration, "", flattened);
        return flattened;
    }

    private void flattenSection(ConfigurationSection section, String prefix, Map<String, String> output) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) {
                flattenSection(nested, fullKey, output);
                continue;
            }
            if (value != null) {
                output.put(fullKey, String.valueOf(value));
            }
        }
    }

    private File languageFile(String languageCode) {
        return new File(langDirectory, languageCode + ".yml");
    }

    private String resolveConfiguredLanguage(String configured, String fallback, String settingName) {
        if (SUPPORTED_LANGUAGES.contains(configured)) {
            return configured;
        }
        logger.warning("Unsupported language '" + configured + "' for language." + settingName + ". Using '" + fallback + "'.");
        return fallback;
    }

    private String normalizeLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return DEFAULT_LANGUAGE_CODE;
        }
        return languageCode.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isLegacyColorCode(char value) {
        char normalized = Character.toLowerCase(value);
        return (normalized >= '0' && normalized <= '9')
                || (normalized >= 'a' && normalized <= 'f')
                || normalized == 'k'
                || normalized == 'l'
                || normalized == 'm'
                || normalized == 'n'
                || normalized == 'o'
                || normalized == 'r'
                || normalized == 'x';
    }
}

package me.erotoro.treechopper;

import java.util.Locale;

public enum ActivationMode {
    ALWAYS_ON,
    SNEAK_DISABLE,
    SNEAK_ENABLE;

    public static ActivationMode parse(String raw, ActivationMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        try {
            return ActivationMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

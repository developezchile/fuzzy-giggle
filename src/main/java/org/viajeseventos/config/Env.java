package org.viajeseventos.config;

/**
 * Resolves a setting through three tiers, first match wins: a real OS environment variable,
 * then the matching key in {@code config.yml} (see {@link ConfigFile}), then the hardcoded
 * default the call site provides. This lets deploy environments override with real env vars
 * while {@code config.yml} carries the checked-in defaults previously hardcoded in
 * {@link AppConfig}.
 */
public final class Env {

    private Env() {
    }

    private static String resolve(String name) {
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        Object fromYaml = ConfigFile.get(name);
        return fromYaml != null ? String.valueOf(fromYaml) : null;
    }

    public static String get(String name, String defaultValue) {
        String value = resolve(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public static long getLong(String name, long defaultValue) {
        String value = resolve(name);
        if (value == null || value.isBlank()) return defaultValue;
        return Long.parseLong(value.trim());
    }

    public static int getInt(String name, int defaultValue) {
        String value = resolve(name);
        if (value == null || value.isBlank()) return defaultValue;
        return Integer.parseInt(value.trim());
    }

    public static double getDouble(String name, double defaultValue) {
        String value = resolve(name);
        if (value == null || value.isBlank()) return defaultValue;
        return Double.parseDouble(value.trim());
    }
}

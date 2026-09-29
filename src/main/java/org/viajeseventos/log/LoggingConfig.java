package org.viajeseventos.log;

import org.viajeseventos.config.Env;
import org.viajeseventos.yaml.SimpleYaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code logging.yml} once at startup and exposes typed settings to {@link Logger}.
 * Look-up order, first match wins:
 * <ol>
 *   <li>the file named by the {@code LOGGING_CONFIG_FILE} env var, if set and readable;</li>
 *   <li>{@code ./logging.yml} in the working directory, if present — lets an operator retune
 *       logging (level, file path, rotation) without rebuilding or repackaging the jar;</li>
 *   <li>the {@code logging.yml} bundled on the classpath (this module's shipped defaults);</li>
 *   <li>hardcoded fallbacks, if none of the above exist or fail to parse.</li>
 * </ol>
 * The {@code LOG_LEVEL} env var, if set, always overrides whatever root level the YAML specifies —
 * consistent with every other setting in {@link org.viajeseventos.config.AppConfig}, where env vars
 * are the final word.
 */
final class LoggingConfig {

    static final LoggingConfig INSTANCE = load();

    final Level rootLevel;
    final Map<String, Level> loggerLevels;
    final boolean consoleEnabled;
    final boolean fileEnabled;
    final String filePath;
    final long maxFileSizeBytes;
    final int maxHistory;

    private LoggingConfig(Level rootLevel, Map<String, Level> loggerLevels, boolean consoleEnabled,
                           boolean fileEnabled, String filePath, long maxFileSizeBytes, int maxHistory) {
        this.rootLevel = rootLevel;
        this.loggerLevels = loggerLevels;
        this.consoleEnabled = consoleEnabled;
        this.fileEnabled = fileEnabled;
        this.filePath = filePath;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxHistory = maxHistory;
    }

    /** Logger names are simple class names ({@link LogManager#getLogger}), so overrides key on those too. */
    Level levelFor(String loggerName) {
        return loggerLevels.getOrDefault(loggerName, rootLevel);
    }

    private static LoggingConfig load() {
        try {
            String text = readConfigText();
            if (text != null) {
                return fromYaml(SimpleYaml.parse(text));
            }
        } catch (Exception e) {
            System.err.println("logging.yml could not be read/parsed, falling back to defaults: " + e.getMessage());
        }
        return defaults();
    }

    private static String readConfigText() throws IOException {
        String explicit = Env.get("LOGGING_CONFIG_FILE", "");
        if (!explicit.isBlank()) {
            Path path = Path.of(explicit);
            if (Files.isReadable(path)) return Files.readString(path);
        }

        Path cwdFile = Path.of("logging.yml");
        if (Files.isReadable(cwdFile)) return Files.readString(cwdFile);

        try (InputStream in = LoggingConfig.class.getResourceAsStream("/logging.yml")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static LoggingConfig fromYaml(Map<String, Object> root) {
        Map<String, Object> logging = (Map<String, Object>) root.getOrDefault("logging", Map.of());

        Level rootLevel = parseLevel(logging.get("level"), Level.INFO);

        Map<String, Level> loggerLevels = new LinkedHashMap<>();
        if (logging.get("loggers") instanceof Map<?, ?> loggersMap) {
            for (var entry : loggersMap.entrySet()) {
                loggerLevels.put(String.valueOf(entry.getKey()), parseLevel(entry.getValue(), rootLevel));
            }
        }

        Map<String, Object> console = (Map<String, Object>) logging.getOrDefault("console", Map.of());
        boolean consoleEnabled = asBoolean(console.get("enabled"), true);

        Map<String, Object> file = (Map<String, Object>) logging.getOrDefault("file", Map.of());
        boolean fileEnabled = asBoolean(file.get("enabled"), false);
        String filePath = String.valueOf(file.getOrDefault("path", "logs/viajes-eventos-api.log"));

        Map<String, Object> rolling = (Map<String, Object>) file.getOrDefault("rolling", Map.of());
        long maxFileSizeBytes = asLong(rolling.get("maxFileSizeMb"), 10) * 1024 * 1024;
        int maxHistory = (int) asLong(rolling.get("maxHistory"), 7);

        rootLevel = Level.valueOf(Env.get("LOG_LEVEL", rootLevel.name()).toUpperCase());

        return new LoggingConfig(rootLevel, loggerLevels, consoleEnabled, fileEnabled, filePath,
                maxFileSizeBytes, maxHistory);
    }

    private static Level parseLevel(Object raw, Level fallback) {
        if (raw == null) return fallback;
        try {
            return Level.valueOf(String.valueOf(raw).toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static boolean asBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) return b;
        if (raw instanceof String s) return Boolean.parseBoolean(s);
        return fallback;
    }

    private static long asLong(Object raw, long fallback) {
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static LoggingConfig defaults() {
        Level level = Level.valueOf(Env.get("LOG_LEVEL", "INFO").toUpperCase());
        return new LoggingConfig(level, Map.of(), true, true, "logs/viajes-eventos-api.log", 10L * 1024 * 1024, 7);
    }
}

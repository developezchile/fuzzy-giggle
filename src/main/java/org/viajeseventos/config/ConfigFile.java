package org.viajeseventos.config;

import org.viajeseventos.yaml.SimpleYaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code config.yml} once at startup and flattens it into a name -&gt; value map that
 * {@link Env} consults as a middle tier between real environment variables and the hardcoded
 * defaults each {@code Env.get*} call site provides. Lookup order for the file itself, first
 * match wins (mirrors {@code org.viajeseventos.log.LoggingConfig}'s handling of logging.yml):
 * <ol>
 *   <li>the file named by the {@code CONFIG_FILE} env var, if set and readable;</li>
 *   <li>{@code ./config.yml} in the working directory — an untracked local/prod override;</li>
 *   <li>the {@code config.yml} bundled on the classpath (this module's shipped defaults).</li>
 * </ol>
 * Nesting in the YAML (server:, database:, ...) is purely for human readability — every leaf
 * key is the env var name it stands in for (e.g. {@code DB_URL}), so grouping has no effect on
 * lookup and a real {@code DB_URL} env var always wins regardless of what's in the file.
 */
final class ConfigFile {

    private static final Map<String, Object> VALUES = load();

    private ConfigFile() {
    }

    static Object get(String name) {
        return VALUES.get(name);
    }

    private static Map<String, Object> load() {
        try {
            String text = readConfigText();
            if (text != null) {
                Map<String, Object> flat = new LinkedHashMap<>();
                flatten(SimpleYaml.parse(text), flat);
                return flat;
            }
        } catch (Exception e) {
            System.err.println("config.yml could not be read/parsed, falling back to hardcoded defaults: " + e.getMessage());
        }
        return Map.of();
    }

    private static String readConfigText() throws IOException {
        String explicit = System.getenv("CONFIG_FILE");
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit);
            if (Files.isReadable(path)) return Files.readString(path);
        }

        Path cwdFile = Path.of("config.yml");
        if (Files.isReadable(cwdFile)) return Files.readString(cwdFile);

        try (InputStream in = ConfigFile.class.getResourceAsStream("/config.yml")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(Map<String, Object> node, Map<String, Object> out) {
        for (var entry : node.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flatten((Map<String, Object>) nested, out);
            } else {
                out.put(entry.getKey(), entry.getValue());
            }
        }
    }
}

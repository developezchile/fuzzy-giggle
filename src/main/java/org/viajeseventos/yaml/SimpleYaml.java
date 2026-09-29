package org.viajeseventos.yaml;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hand-rolled parser for the small YAML subset this module's config files need:
 * indentation-nested {@code key: value} mappings, comments, quoted/boolean/numeric scalars —
 * no lists, anchors, or flow style. Written by hand (like {@link org.viajeseventos.json.Json})
 * instead of pulling in SnakeYAML, so this module has zero reflection-based dependencies.
 * Shared by {@code org.viajeseventos.log.LoggingConfig} (logging.yml) and
 * {@code org.viajeseventos.config.ConfigFile} (config.yml).
 */
public final class SimpleYaml {

    private SimpleYaml() {
    }

    public static Map<String, Object> parse(String text) {
        Map<String, Object> root = new LinkedHashMap<>();
        Deque<Map<String, Object>> mapStack = new ArrayDeque<>();
        Deque<Integer> levelStack = new ArrayDeque<>();
        mapStack.push(root);
        levelStack.push(-1);

        for (String rawLine : text.split("\n", -1)) {
            String line = stripComment(rawLine);
            if (line.isBlank()) continue;
            int indent = indentOf(line);
            String trimmed = line.strip();
            int colon = findColon(trimmed);
            if (colon < 0) continue;
            String key = trimmed.substring(0, colon).strip();
            String rawValue = trimmed.substring(colon + 1).strip();

            while (indent <= levelStack.peek()) {
                mapStack.pop();
                levelStack.pop();
            }
            Map<String, Object> current = mapStack.peek();

            if (rawValue.isEmpty()) {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(key, nested);
                mapStack.push(nested);
                levelStack.push(indent);
            } else {
                current.put(key, parseScalar(rawValue));
            }
        }
        return root;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static int findColon(String trimmed) {
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (inQuotes) {
                if (c == quoteChar) inQuotes = false;
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (c == ':') {
                return i;
            }
        }
        return -1;
    }

    private static Object parseScalar(String raw) {
        if (raw.length() >= 2 && ((raw.startsWith("\"") && raw.endsWith("\""))
                || (raw.startsWith("'") && raw.endsWith("'")))) {
            return raw.substring(1, raw.length() - 1);
        }
        if (raw.equals("true") || raw.equals("false")) return Boolean.parseBoolean(raw);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }
}

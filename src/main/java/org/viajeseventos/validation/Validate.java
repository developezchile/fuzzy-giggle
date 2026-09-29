package org.viajeseventos.validation;

import org.viajeseventos.exception.ValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Tiny bean-validation stand-in: request DTOs call these while building themselves from JSON. */
public final class Validate {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Validate() {
    }

    public static Map<String, String> newErrors() {
        return new LinkedHashMap<>();
    }

    public static void notBlank(Map<String, String> errors, String field, String value) {
        if (value == null || value.isBlank()) {
            errors.put(field, "no debe estar vacío");
        }
    }

    public static void email(Map<String, String> errors, String field, String value) {
        if (value != null && !value.isBlank() && !EMAIL_PATTERN.matcher(value).matches()) {
            errors.put(field, "debe ser una dirección de correo válida");
        }
    }

    public static void minLength(Map<String, String> errors, String field, String value, int min) {
        if (value != null && value.length() < min) {
            errors.put(field, "debe tener al menos " + min + " caracteres");
        }
    }

    public static void maxLength(Map<String, String> errors, String field, String value, int max) {
        if (value != null && value.length() > max) {
            errors.put(field, "no debe superar los " + max + " caracteres");
        }
    }

    public static void matches(Map<String, String> errors, String field, String value, Pattern pattern, String message) {
        if (value != null && !value.isBlank() && !pattern.matcher(value).matches()) {
            errors.put(field, message);
        }
    }

    public static String str(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** Trimmed string, with blank collapsed to null — for optional free-text fields. */
    public static String optStr(Map<String, Object> json, String key) {
        String value = str(json, key);
        if (value == null) return null;
        value = value.strip();
        return value.isEmpty() ? null : value;
    }

    /** JSON numbers arrive as {@code Double}; accepts whole numbers only, records an error otherwise. */
    public static Long longVal(Map<String, String> errors, Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return null;
        if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.longValue();
        }
        errors.put(key, "debe ser un número entero");
        return null;
    }

    public static Boolean boolVal(Map<String, String> errors, Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        errors.put(key, "debe ser verdadero o falso");
        return null;
    }

    /** A JSON array of strings; records an error (and returns null) for anything else. */
    public static List<String> strList(Map<String, String> errors, Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value == null) return null;
        if (!(value instanceof List<?> list)) {
            errors.put(key, "debe ser una lista");
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String s)) {
                errors.put(key, "debe ser una lista de textos");
                return null;
            }
            result.add(s);
        }
        return result;
    }

    public static void notNull(Map<String, String> errors, String field, Object value) {
        if (value == null && !errors.containsKey(field)) {
            errors.put(field, "es obligatorio");
        }
    }

    /** Parses an ISO {@code YYYY-MM-DD} string, recording a validation error instead of throwing. */
    public static LocalDate dateVal(Map<String, String> errors, String field, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.put(field, "debe tener el formato AAAA-MM-DD");
            return null;
        }
    }

    public static void check(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}

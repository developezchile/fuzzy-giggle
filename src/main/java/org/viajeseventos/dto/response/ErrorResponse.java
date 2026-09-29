package org.viajeseventos.dto.response;

import org.viajeseventos.json.Json;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ErrorResponse {

    private final String errorCode;
    private final String message;
    private final Map<String, String> errors;
    private final String path;

    private ErrorResponse(String errorCode, String message, Map<String, String> errors, String path) {
        this.errorCode = errorCode;
        this.message = message;
        this.errors = errors;
        this.path = path;
    }

    public static ErrorResponse of(String errorCode, String message, String path) {
        return new ErrorResponse(errorCode, message, null, path);
    }

    public static ErrorResponse ofValidation(String errorCode, String message, String path, Map<String, String> errors) {
        return new ErrorResponse(errorCode, message, errors, path);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("success", false);
        map.put("errorCode", errorCode);
        map.put("message", message);
        if (errors != null) {
            map.put("errors", new LinkedHashMap<>(errors));
        }
        map.put("timestamp", LocalDateTime.now().toString());
        map.put("path", path);
        return map;
    }
}

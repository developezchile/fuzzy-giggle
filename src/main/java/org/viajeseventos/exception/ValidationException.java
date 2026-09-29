package org.viajeseventos.exception;

import java.util.Map;

public final class ValidationException extends ApiException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super(400, "VALIDATION_ERROR", "Error de validación");
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

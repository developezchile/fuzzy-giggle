package org.viajeseventos.exception;

/** Authenticated, but not allowed to do this. */
public final class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(403, "FORBIDDEN", message);
    }
}

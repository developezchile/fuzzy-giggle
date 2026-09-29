package org.viajeseventos.exception;

/** Username/email already taken, duplicate resource, etc. */
public final class DuplicateResourceException extends ApiException {
    public DuplicateResourceException(String message) {
        super(409, "DUPLICATE_RESOURCE", message);
    }
}

package org.viajeseventos.exception;

/** Missing/invalid/expired bearer token. */
public final class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(401, "UNAUTHORIZED", message);
    }
}

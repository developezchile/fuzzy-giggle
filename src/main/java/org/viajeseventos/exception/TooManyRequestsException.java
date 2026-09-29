package org.viajeseventos.exception;

/** Rate limit exceeded on a sensitive endpoint (login, register, password reset, ...). */
public final class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(429, "TOO_MANY_REQUESTS", message);
    }
}

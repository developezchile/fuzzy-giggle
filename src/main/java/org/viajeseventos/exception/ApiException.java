package org.viajeseventos.exception;

/** Base for exceptions that carry an HTTP status + machine-readable error code to the controller layer. */
public abstract class ApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    protected ApiException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

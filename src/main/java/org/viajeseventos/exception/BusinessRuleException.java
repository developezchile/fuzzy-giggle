package org.viajeseventos.exception;

/**
 * A request that's well-formed but violates a business rule (bad login, invalid state, unverified
 * account). Defaults to 409 (state conflict); use {@link #badRequest(String)} for plain validation
 * failures.
 */
public final class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(409, "BUSINESS_RULE_VIOLATION", message);
    }

    private BusinessRuleException(int statusCode, String message) {
        super(statusCode, "VALIDATION_ERROR", message);
    }

    public static BusinessRuleException badRequest(String message) {
        return new BusinessRuleException(400, message);
    }
}

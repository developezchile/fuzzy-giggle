package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

public final class ResendVerificationRequest {

    public final String email;

    private ResendVerificationRequest(String email) {
        this.email = email;
    }

    public static ResendVerificationRequest fromJson(Map<String, Object> json) {
        String email = str(json, "email");
        var errors = newErrors();
        notBlank(errors, "email", email);
        email(errors, "email", email);
        check(errors);
        return new ResendVerificationRequest(email);
    }
}

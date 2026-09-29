package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

public final class ForgotPasswordRequest {

    public final String email;

    private ForgotPasswordRequest(String email) {
        this.email = email;
    }

    public static ForgotPasswordRequest fromJson(Map<String, Object> json) {
        String email = str(json, "email");
        var errors = newErrors();
        notBlank(errors, "email", email);
        email(errors, "email", email);
        check(errors);
        return new ForgotPasswordRequest(email);
    }
}

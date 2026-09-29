package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

public final class VerifyEmailRequest {

    public final String token;

    private VerifyEmailRequest(String token) {
        this.token = token;
    }

    public static VerifyEmailRequest fromJson(Map<String, Object> json) {
        String token = str(json, "token");
        var errors = newErrors();
        notBlank(errors, "token", token);
        check(errors);
        return new VerifyEmailRequest(token);
    }
}

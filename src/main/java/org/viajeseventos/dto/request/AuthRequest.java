package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

public final class AuthRequest {

    public final String email;
    public final String password;

    private AuthRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public static AuthRequest fromJson(Map<String, Object> json) {
        String email = str(json, "email");
        String password = str(json, "password");

        var errors = newErrors();
        notBlank(errors, "email", email);
        email(errors, "email", email);
        notBlank(errors, "password", password);
        check(errors);

        return new AuthRequest(email, password);
    }
}

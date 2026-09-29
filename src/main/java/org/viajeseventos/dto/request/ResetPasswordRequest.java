package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

public final class ResetPasswordRequest {

    public final String token;
    public final String newPassword;

    private ResetPasswordRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public static ResetPasswordRequest fromJson(Map<String, Object> json) {
        String token = str(json, "token");
        String newPassword = str(json, "newPassword");
        var errors = newErrors();
        notBlank(errors, "token", token);
        notBlank(errors, "newPassword", newPassword);
        minLength(errors, "newPassword", newPassword, 6);
        check(errors);
        return new ResetPasswordRequest(token, newPassword);
    }
}

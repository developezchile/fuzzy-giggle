package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

public final class ChangePasswordRequest {

    public final String currentPassword;
    public final String newPassword;

    private ChangePasswordRequest(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    public static ChangePasswordRequest fromJson(Map<String, Object> json) {
        String currentPassword = str(json, "currentPassword");
        String newPassword = str(json, "newPassword");

        var errors = newErrors();
        notBlank(errors, "currentPassword", currentPassword);
        notBlank(errors, "newPassword", newPassword);
        minLength(errors, "newPassword", newPassword, 6);
        check(errors);

        return new ChangePasswordRequest(currentPassword, newPassword);
    }
}

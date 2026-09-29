package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/**
 * Account created by an administrator (USERS module) with an explicit profile — unlike
 * self-registration, which always lands in CLIENT. The email counts as verified, since the admin
 * is vouching for it.
 */
public final class CreateUserRequest {

    public final String username;
    public final String email;
    public final String password;
    public final String firstName;
    public final String lastName;
    public final String phone;
    public final long profileId;

    private CreateUserRequest(String username, String email, String password, String firstName,
                               String lastName, String phone, long profileId) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.profileId = profileId;
    }

    public static CreateUserRequest fromJson(Map<String, Object> json) {
        String username = optStr(json, "username");
        String email = optStr(json, "email");
        String password = str(json, "password");
        String firstName = optStr(json, "firstName");
        String lastName = optStr(json, "lastName");
        String phone = optStr(json, "phone");

        var errors = newErrors();
        notBlank(errors, "username", username);
        minLength(errors, "username", username, 3);
        maxLength(errors, "username", username, 50);
        notBlank(errors, "email", email);
        email(errors, "email", email);
        notBlank(errors, "password", password);
        minLength(errors, "password", password, 6);
        Long profileId = longVal(errors, json, "profileId");
        notNull(errors, "profileId", profileId);
        check(errors);

        return new CreateUserRequest(username, email, password, firstName, lastName, phone, profileId);
    }
}

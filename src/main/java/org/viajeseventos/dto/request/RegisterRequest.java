package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/**
 * Self-registration always lands the new account in the {@code CLIENT} profile — the profile is
 * not client-supplied, so a request body can't grant itself {@code ADMIN}. Assigning another
 * profile is done by an administrator from the USERS module.
 */
public final class RegisterRequest {

    public final String username;
    public final String email;
    public final String password;
    public final String firstName;
    public final String lastName;
    public final String phone;

    private RegisterRequest(String username, String email, String password, String firstName,
                             String lastName, String phone) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public static RegisterRequest fromJson(Map<String, Object> json) {
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
        maxLength(errors, "firstName", firstName, 100);
        maxLength(errors, "lastName", lastName, 100);
        maxLength(errors, "phone", phone, 30);
        check(errors);

        return new RegisterRequest(username, email, password, firstName, lastName, phone);
    }
}

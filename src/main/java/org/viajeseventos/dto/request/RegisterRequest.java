package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/**
 * A client signing up through their transport company's link ({@code /empresa/<slug>}): the account
 * lands in that company, always with the {@code CLIENT} profile — the profile is not client-supplied,
 * so a request body can't grant itself {@code ADMIN}. Assigning another profile is done by the
 * company's administrator from the USERS module.
 */
public final class RegisterRequest {

    public final String username;
    public final String email;
    public final String password;
    public final String firstName;
    public final String lastName;
    public final String phone;
    /** The company's slug, from the registration link. */
    public final String company;

    private RegisterRequest(String username, String email, String password, String firstName,
                             String lastName, String phone, String company) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.company = company;
    }

    public static RegisterRequest fromJson(Map<String, Object> json) {
        var errors = newErrors();
        RegisterRequest request = parseAccount(json, errors);
        notBlank(errors, "company", request.company);
        check(errors);
        return request;
    }

    /** The account fields, validated into {@code errors} — shared with {@link RegisterCompanyRequest}. */
    static RegisterRequest parseAccount(Map<String, Object> json, Map<String, String> errors) {
        String username = optStr(json, "username");
        String email = optStr(json, "email");
        String password = str(json, "password");
        String firstName = optStr(json, "firstName");
        String lastName = optStr(json, "lastName");
        String phone = optStr(json, "phone");
        String company = optStr(json, "company");

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

        return new RegisterRequest(username, email, password, firstName, lastName, phone, company);
    }
}

package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/** The logged-in user editing their own contact data. Profile and email aren't self-editable. */
public final class UpdateMeRequest {

    public final String firstName;
    public final String lastName;
    public final String phone;

    private UpdateMeRequest(String firstName, String lastName, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public static UpdateMeRequest fromJson(Map<String, Object> json) {
        String firstName = optStr(json, "firstName");
        String lastName = optStr(json, "lastName");
        String phone = optStr(json, "phone");

        var errors = newErrors();
        maxLength(errors, "firstName", firstName, 100);
        maxLength(errors, "lastName", lastName, 100);
        maxLength(errors, "phone", phone, 30);
        check(errors);

        return new UpdateMeRequest(firstName, lastName, phone);
    }
}

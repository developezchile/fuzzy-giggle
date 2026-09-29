package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/** Administrator edit of an account (USERS module): contact data, assigned profile, enabled flag. */
public final class UpdateUserRequest {

    public final String firstName;
    public final String lastName;
    public final String phone;
    public final long profileId;
    public final boolean enabled;

    private UpdateUserRequest(String firstName, String lastName, String phone, long profileId, boolean enabled) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.profileId = profileId;
        this.enabled = enabled;
    }

    public static UpdateUserRequest fromJson(Map<String, Object> json) {
        String firstName = optStr(json, "firstName");
        String lastName = optStr(json, "lastName");
        String phone = optStr(json, "phone");

        var errors = newErrors();
        maxLength(errors, "firstName", firstName, 100);
        maxLength(errors, "lastName", lastName, 100);
        maxLength(errors, "phone", phone, 30);
        Long profileId = longVal(errors, json, "profileId");
        notNull(errors, "profileId", profileId);
        Boolean enabled = boolVal(errors, json, "enabled");
        notNull(errors, "enabled", enabled);
        check(errors);

        return new UpdateUserRequest(firstName, lastName, phone, profileId, enabled);
    }
}

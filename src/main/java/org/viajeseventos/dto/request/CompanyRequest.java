package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/** A company administrator editing their company (COMPANY module). The slug can't change. */
public final class CompanyRequest {

    public final String name;
    public final String contactEmail;

    private CompanyRequest(String name, String contactEmail) {
        this.name = name;
        this.contactEmail = contactEmail;
    }

    public static CompanyRequest fromJson(Map<String, Object> json) {
        String name = optStr(json, "name");
        String contactEmail = optStr(json, "contactEmail");

        var errors = newErrors();
        notBlank(errors, "name", name);
        minLength(errors, "name", name, 2);
        maxLength(errors, "name", name, 150);
        if (contactEmail != null) {
            email(errors, "contactEmail", contactEmail);
            maxLength(errors, "contactEmail", contactEmail, 255);
        }
        check(errors);

        return new CompanyRequest(name, contactEmail);
    }
}

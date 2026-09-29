package org.viajeseventos.dto.request;

import org.viajeseventos.validation.Slugs;

import java.util.Locale;
import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

/**
 * A transport company signing itself up: the company plus its first administrator's account.
 * {@code companySlug} is optional — derived from the name when omitted.
 */
public final class RegisterCompanyRequest {

    public final String companyName;
    public final String companySlug;
    public final RegisterRequest account;

    private RegisterCompanyRequest(String companyName, String companySlug, RegisterRequest account) {
        this.companyName = companyName;
        this.companySlug = companySlug;
        this.account = account;
    }

    public static RegisterCompanyRequest fromJson(Map<String, Object> json) {
        var errors = newErrors();
        RegisterRequest account = RegisterRequest.parseAccount(json, errors);
        String companyName = optStr(json, "companyName");
        String companySlug = optStr(json, "companySlug");
        if (companySlug != null) companySlug = companySlug.toLowerCase(Locale.ROOT);

        notBlank(errors, "companyName", companyName);
        minLength(errors, "companyName", companyName, 2);
        maxLength(errors, "companyName", companyName, 150);
        if (companySlug != null) {
            minLength(errors, "companySlug", companySlug, 3);
            maxLength(errors, "companySlug", companySlug, 60);
            matches(errors, "companySlug", companySlug, Slugs.PATTERN,
                    "Solo letras minúsculas, números y guiones (por ejemplo buses-lopez)");
        }
        check(errors);

        return new RegisterCompanyRequest(companyName, companySlug, account);
    }
}

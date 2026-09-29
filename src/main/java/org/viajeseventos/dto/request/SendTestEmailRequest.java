package org.viajeseventos.dto.request;

import java.util.Map;

import static org.viajeseventos.validation.Validate.*;

public final class SendTestEmailRequest {

    public final String to;

    private SendTestEmailRequest(String to) {
        this.to = to;
    }

    public static SendTestEmailRequest fromJson(Map<String, Object> json) {
        String to = str(json, "to");
        var errors = newErrors();
        notBlank(errors, "to", to);
        email(errors, "to", to);
        check(errors);
        return new SendTestEmailRequest(to);
    }
}

package org.viajeseventos.dto.request;

import org.viajeseventos.model.AppModule;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.viajeseventos.validation.Validate.*;

/** Create/update body for the profile maintainer: a name plus the modules it grants. */
public final class ProfileRequest {

    public final String name;
    public final String description;
    public final Set<AppModule> modules;

    private ProfileRequest(String name, String description, Set<AppModule> modules) {
        this.name = name;
        this.description = description;
        this.modules = modules;
    }

    public static ProfileRequest fromJson(Map<String, Object> json) {
        String name = optStr(json, "name");
        String description = optStr(json, "description");

        var errors = newErrors();
        notBlank(errors, "name", name);
        maxLength(errors, "name", name, 100);
        maxLength(errors, "description", description, 500);
        List<String> moduleKeys = strList(errors, json, "modules");
        notNull(errors, "modules", moduleKeys);

        Set<AppModule> modules = EnumSet.noneOf(AppModule.class);
        if (moduleKeys != null) {
            for (String key : moduleKeys) {
                AppModule.fromKey(key).ifPresentOrElse(modules::add,
                        () -> errors.put("modules", "módulo desconocido: " + key));
            }
        }
        check(errors);

        return new ProfileRequest(name, description, modules);
    }
}

package org.viajeseventos.dto.response;

import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.model.Profile;

import java.util.Map;

public final class ProfileResponse {

    private ProfileResponse() {
    }

    public static Map<String, Object> from(Profile profile) {
        Map<String, Object> map = Json.obj();
        map.put("id", profile.getId());
        map.put("code", profile.getCode());
        map.put("name", profile.getName());
        map.put("description", profile.getDescription());
        map.put("system", profile.isSystem());
        // ADMIN always holds every module (see ProfileService) — the maintainer shows it read-only.
        map.put("editableModules", !profile.isAdmin());
        map.put("deletable", !profile.isSystem());
        map.put("modules", profile.getModules().stream().map(AppModule::name).toList());
        map.put("userCount", profile.getUserCount());
        map.put("createdAt", profile.getCreatedAt());
        map.put("updatedAt", profile.getUpdatedAt());
        return map;
    }

    /** The module catalog the maintainer renders as checkboxes. */
    public static Map<String, Object> module(AppModule module) {
        Map<String, Object> map = Json.obj();
        map.put("key", module.name());
        map.put("label", module.label());
        map.put("description", module.description());
        return map;
    }
}

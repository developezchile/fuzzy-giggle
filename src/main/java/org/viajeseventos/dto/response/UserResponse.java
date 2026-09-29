package org.viajeseventos.dto.response;

import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.model.User;

import java.util.Map;
import java.util.Set;

/** A user as returned by the API. Never includes the password hash. */
public final class UserResponse {

    private UserResponse() {
    }

    /** For listings: account data plus which profile it has. */
    public static Map<String, Object> from(User user) {
        Map<String, Object> map = Json.obj();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("firstName", user.getFirstName());
        map.put("lastName", user.getLastName());
        map.put("phone", user.getPhone());
        map.put("enabled", user.isEnabled());
        map.put("emailVerified", user.isEmailVerified());
        map.put("createdAt", user.getCreatedAt());

        Map<String, Object> profile = Json.obj();
        profile.put("id", user.getProfileId());
        profile.put("code", user.getProfileCode());
        profile.put("name", user.getProfileName());
        map.put("profile", profile);
        return map;
    }

    /**
     * For the session ({@code /auth/me}, login): also the modules the profile grants, which the
     * frontend uses to build its navigation and guard its pages.
     */
    public static Map<String, Object> withModules(User user, Set<AppModule> modules) {
        Map<String, Object> map = from(user);
        map.put("modules", modules.stream().map(AppModule::name).toList());
        return map;
    }
}

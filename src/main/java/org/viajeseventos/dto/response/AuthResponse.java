package org.viajeseventos.dto.response;

import org.viajeseventos.json.Json;

import java.util.Map;

/** Login result: the bearer token plus the session user (with modules) so the UI needn't call /auth/me right after. */
public final class AuthResponse {

    public final String token;
    public final Map<String, Object> user;

    public AuthResponse(String token, Map<String, Object> user) {
        this.token = token;
        this.user = user;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = Json.obj();
        map.put("token", token);
        map.put("user", user);
        return map;
    }
}

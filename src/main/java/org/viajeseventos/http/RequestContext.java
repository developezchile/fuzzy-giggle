package org.viajeseventos.http;

import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.UnauthorizedException;
import org.viajeseventos.json.Json;
import org.viajeseventos.security.Caller;
import org.viajeseventos.security.JwtService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-request facade over {@link HttpExchange}: path/query params, body parsing, auth. */
public final class RequestContext {

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final JwtService jwtService;
    private String cachedBody;
    private Caller caller;

    RequestContext(HttpExchange exchange, Map<String, String> pathParams, JwtService jwtService) {
        this.exchange = exchange;
        this.pathParams = pathParams;
        this.jwtService = jwtService;
    }

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public Long pathParamLong(String name) {
        String value = pathParam(name);
        if (value == null) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw BusinessRuleException.badRequest("El identificador '" + value + "' no es válido");
        }
    }

    public String queryParam(String name) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) return null;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            if (URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    /** Best-effort caller IP for rate limiting — the socket peer address, not X-Forwarded-For
     *  (this process isn't expected to sit behind a proxy that sets it trustworthily). */
    public String clientIp() {
        var remote = exchange.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }

    public String body() {
        if (cachedBody == null) {
            try (InputStream in = exchange.getRequestBody()) {
                cachedBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read request body", e);
            }
        }
        return cachedBody;
    }

    public Map<String, Object> jsonBody() {
        String raw = body();
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        return Json.parseObject(raw);
    }

    /**
     * Validates the Bearer token from the Authorization header and returns the authenticated user id.
     * Authorization (what the user may do) is not in the token — it's the user's profile modules,
     * checked by {@link org.viajeseventos.security.ModuleAccess}.
     */
    public long requireUserId() {
        return jwtService.userIdFromToken(bearerToken());
    }

    void setCaller(Caller caller) {
        this.caller = caller;
    }

    /** The caller of a route registered with a module — set by the {@link Router} once access is granted. */
    public Caller caller() {
        if (caller == null) {
            throw new IllegalStateException("caller() is only available on routes registered with a module");
        }
        return caller;
    }

    private String bearerToken() {
        String auth = header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new UnauthorizedException("Falta el token de autenticación");
        }
        String token = auth.substring("Bearer ".length()).trim();
        if (!jwtService.validate(token)) {
            throw new UnauthorizedException("Token inválido o expirado");
        }
        return token;
    }
}

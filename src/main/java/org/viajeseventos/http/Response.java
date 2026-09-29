package org.viajeseventos.http;

import java.util.Map;

/** What a {@link RouteHandler} returns: an HTTP status, a body, how to serialize it, and any extra headers. */
public record Response(int status, Object body, String contentType, Map<String, String> headers) {

    public static final String JSON = "application/json; charset=utf-8";

    public static Response ok(Object body) {
        return new Response(200, body, JSON, Map.of());
    }

    public static Response created(Object body) {
        return new Response(201, body, JSON, Map.of());
    }

    /** A response with no body at all (e.g. after a DELETE) — contentType is deliberately non-JSON
     *  so the router writes the raw empty string as-is instead of JSON-encoding it to {@code "null"}. */
    public static Response noContent() {
        return new Response(204, "", "text/plain; charset=utf-8", Map.of());
    }

    /** A response whose body is already-serialized text and must not be JSON-encoded. */
    public static Response raw(int status, String contentType, String content) {
        return new Response(status, content, contentType, Map.of());
    }

    /** A redirect (302 Found) — no body, just a {@code Location} header. */
    public static Response redirect(String location) {
        return new Response(302, "", "text/plain; charset=utf-8", Map.of("Location", location));
    }
}

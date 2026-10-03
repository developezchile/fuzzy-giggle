package org.viajeseventos.http;

import org.viajeseventos.dto.response.ErrorResponse;
import org.viajeseventos.exception.ApiException;
import org.viajeseventos.exception.ValidationException;
import org.viajeseventos.json.Json;
import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.security.JwtService;
import org.viajeseventos.security.ModuleAccess;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny path-pattern router registered as a single {@link HttpHandler} on the JDK's
 * {@code com.sun.net.httpserver.HttpServer}. Supports {@code {name}} path segments,
 * turns thrown {@link ApiException}s into a JSON error envelope, and handles CORS preflight.
 *
 * <p>Routes registered with an {@link AppModule} are gated by {@link ModuleAccess} before their
 * handler runs — the declarative equivalent of tagging a Spring controller with
 * {@code @RequiresModule}. Routes registered without one are public, or check auth themselves.
 */
public final class Router implements HttpHandler {

    private static final Logger log = LogManager.getLogger(Router.class);
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)}");

    private final List<Route> routes = new ArrayList<>();
    private final JwtService jwtService;
    private final ModuleAccess moduleAccess;
    private final List<String> allowedOrigins;
    private final String contextPath;

    public Router(JwtService jwtService, ModuleAccess moduleAccess, List<String> allowedOrigins, String contextPath) {
        this.jwtService = jwtService;
        this.moduleAccess = moduleAccess;
        this.allowedOrigins = allowedOrigins;
        this.contextPath = contextPath;
    }

    public void get(String pathPattern, RouteHandler handler) {
        add("GET", pathPattern, null, handler);
    }

    public void get(String pathPattern, AppModule module, RouteHandler handler) {
        add("GET", pathPattern, module, handler);
    }

    public void post(String pathPattern, RouteHandler handler) {
        add("POST", pathPattern, null, handler);
    }

    public void post(String pathPattern, AppModule module, RouteHandler handler) {
        add("POST", pathPattern, module, handler);
    }

    public void put(String pathPattern, RouteHandler handler) {
        add("PUT", pathPattern, null, handler);
    }

    public void put(String pathPattern, AppModule module, RouteHandler handler) {
        add("PUT", pathPattern, module, handler);
    }

    public void patch(String pathPattern, RouteHandler handler) {
        add("PATCH", pathPattern, null, handler);
    }

    public void patch(String pathPattern, AppModule module, RouteHandler handler) {
        add("PATCH", pathPattern, module, handler);
    }

    public void delete(String pathPattern, RouteHandler handler) {
        add("DELETE", pathPattern, null, handler);
    }

    public void delete(String pathPattern, AppModule module, RouteHandler handler) {
        add("DELETE", pathPattern, module, handler);
    }

    private void add(String method, String pathPattern, AppModule module, RouteHandler handler) {
        List<String> paramNames = new ArrayList<>();
        Matcher m = PARAM_PATTERN.matcher(pathPattern);
        String regex = pathPattern;
        while (m.find()) {
            paramNames.add(m.group(1));
        }
        regex = PARAM_PATTERN.matcher(regex).replaceAll("([^/]+)");
        routes.add(new Route(method, Pattern.compile("^" + regex + "$"), paramNames, module, handler));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long start = System.nanoTime();
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        applyCors(exchange, origin);

        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String rawPath = exchange.getRequestURI().getPath();
        String path = stripContextPath(rawPath);

        for (Route route : routes) {
            if (!route.method.equalsIgnoreCase(method)) continue;
            Matcher matcher = route.pattern.matcher(path);
            if (!matcher.matches()) continue;

            Map<String, String> pathParams = new LinkedHashMap<>();
            for (int i = 0; i < route.paramNames.size(); i++) {
                pathParams.put(route.paramNames.get(i), matcher.group(i + 1));
            }
            dispatch(exchange, route, pathParams, method, rawPath, start);
            return;
        }

        int status = 404;
        writeJson(exchange, status, ErrorResponse.of("NOT_FOUND", "Recurso no encontrado", path).toMap());
        logAccess(method, rawPath, status, start);
    }

    private void dispatch(HttpExchange exchange, Route route, Map<String, String> pathParams,
                           String method, String path, long start) throws IOException {
        RequestContext ctx = new RequestContext(exchange, pathParams, jwtService);
        int status;
        try {
            if (route.module != null) {
                ctx.setCaller(moduleAccess.require(ctx, route.module));
            }
            Response response = route.handler.handle(ctx);
            status = response.status();
            write(exchange, status, response.contentType(), response.body(), response.headers());
        } catch (ValidationException e) {
            status = e.getStatusCode();
            writeJson(exchange, status,
                    ErrorResponse.ofValidation(e.getErrorCode(), e.getMessage(), path, e.getErrors()).toMap());
        } catch (ApiException e) {
            status = e.getStatusCode();
            writeJson(exchange, status, ErrorResponse.of(e.getErrorCode(), e.getMessage(), path).toMap());
        } catch (Exception e) {
            status = 500;
            log.error("Unhandled error on {}", e, path);
            writeJson(exchange, status, ErrorResponse.of("INTERNAL_ERROR", "Error interno del servidor", path).toMap());
        }
        logAccess(method, path, status, start);
    }

    /** One line per request: {@code METHOD path status durationMs}. */
    private void logAccess(String method, String path, int status, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("{} {} {} {}ms", method, path, status, durationMs);
    }

    private String stripContextPath(String path) {
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            String stripped = path.substring(contextPath.length());
            return stripped.isEmpty() ? "/" : stripped;
        }
        return path;
    }

    private void applyCors(HttpExchange exchange, String origin) {
        if (origin != null && (allowedOrigins.contains("*") || allowedOrigins.contains(origin))) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        write(exchange, status, Response.JSON, body, Map.of());
    }

    private void write(HttpExchange exchange, int status, String contentType, Object body,
                        Map<String, String> extraHeaders) throws IOException {
        byte[] bytes;
        if (body instanceof byte[] raw) {
            bytes = raw;
        } else {
            String text = Response.JSON.equals(contentType) ? Json.write(body) : (String) body;
            bytes = text.getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        extraHeaders.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            exchange.close();
        }
    }

    private record Route(String method, Pattern pattern, List<String> paramNames, AppModule module,
                         RouteHandler handler) {
    }
}

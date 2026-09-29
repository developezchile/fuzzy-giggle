package org.viajeseventos.http;

/** A handler for one (method, path pattern) route. */
@FunctionalInterface
public interface RouteHandler {
    Response handle(RequestContext ctx) throws Exception;
}

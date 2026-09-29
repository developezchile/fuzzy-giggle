package org.viajeseventos.controller;

import org.viajeseventos.dto.request.CreateUserRequest;
import org.viajeseventos.dto.request.UpdateUserRequest;
import org.viajeseventos.dto.response.UserResponse;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.http.Response;
import org.viajeseventos.http.Router;
import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.service.AuthService;
import org.viajeseventos.service.UserService;

import java.util.Map;

/** Account administration — every route requires the USERS module. */
public final class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    public void register(Router router) {
        // Before /users/{id} — routes match in registration order.
        router.get("/users/profile-options", AppModule.USERS, this::profileOptions);
        router.get("/users", AppModule.USERS, this::list);
        router.get("/users/{id}", AppModule.USERS, this::get);
        router.post("/users", AppModule.USERS, this::create);
        router.put("/users/{id}", AppModule.USERS, this::update);
        router.post("/users/{id}/verify-email", AppModule.USERS, this::verifyEmail);
        router.post("/users/{id}/resend-verification", AppModule.USERS, this::resendVerification);
    }

    private Response profileOptions(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("profiles", userService.profileOptions().stream().map(p -> {
            Map<String, Object> option = Json.obj();
            option.put("id", p.getId());
            option.put("code", p.getCode());
            option.put("name", p.getName());
            return option;
        }).toList());
        return Response.ok(body);
    }

    private Response list(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("users", userService.findAll().stream().map(UserResponse::from).toList());
        return Response.ok(body);
    }

    private Response get(RequestContext ctx) {
        return Response.ok(UserResponse.from(userService.findById(ctx.pathParamLong("id"))));
    }

    private Response create(RequestContext ctx) {
        return Response.created(UserResponse.from(userService.create(CreateUserRequest.fromJson(ctx.jsonBody()))));
    }

    private Response update(RequestContext ctx) {
        long callerId = ctx.requireUserId();
        var user = userService.update(callerId, ctx.pathParamLong("id"), UpdateUserRequest.fromJson(ctx.jsonBody()));
        return Response.ok(UserResponse.from(user));
    }

    private Response verifyEmail(RequestContext ctx) {
        long id = ctx.pathParamLong("id");
        authService.markEmailVerified(id);
        return Response.ok(UserResponse.from(userService.findById(id)));
    }

    private Response resendVerification(RequestContext ctx) {
        long id = ctx.pathParamLong("id");
        authService.resendVerificationTo(id);
        Map<String, Object> body = Json.obj();
        body.put("message", "Enviamos un nuevo enlace de verificación a " + userService.findById(id).getEmail());
        return Response.ok(body);
    }
}

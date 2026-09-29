package org.viajeseventos.controller;

import org.viajeseventos.dto.request.AuthRequest;
import org.viajeseventos.dto.request.ChangePasswordRequest;
import org.viajeseventos.dto.request.ForgotPasswordRequest;
import org.viajeseventos.dto.request.RegisterRequest;
import org.viajeseventos.dto.request.ResendVerificationRequest;
import org.viajeseventos.dto.request.ResetPasswordRequest;
import org.viajeseventos.dto.request.UpdateMeRequest;
import org.viajeseventos.dto.request.VerifyEmailRequest;
import org.viajeseventos.exception.TooManyRequestsException;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.http.Response;
import org.viajeseventos.http.Router;
import org.viajeseventos.json.Json;
import org.viajeseventos.security.RateLimiter;
import org.viajeseventos.service.AuthService;

import java.util.Map;

public final class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    public AuthController(AuthService authService, RateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    public void register(Router router) {
        router.post("/auth/register", this::registerUser);
        router.post("/auth/login", this::login);
        router.post("/auth/verify-email", this::verifyEmail);
        router.post("/auth/resend-verification", this::resendVerification);
        router.post("/auth/forgot-password", this::forgotPassword);
        router.post("/auth/reset-password", this::resetPassword);
        // Session + "mi perfil": any logged-in user, no module required.
        router.get("/auth/me", this::me);
        router.put("/auth/me", this::updateMe);
        router.post("/auth/change-password", this::changePassword);
    }

    private Response registerUser(RequestContext ctx) {
        limit(ctx, "register");
        RegisterRequest request = RegisterRequest.fromJson(ctx.jsonBody());
        authService.register(request);
        return Response.created(message("Cuenta creada correctamente. Revisa tu correo electrónico para verificar tu cuenta antes de iniciar sesión."));
    }

    private Response login(RequestContext ctx) {
        limit(ctx, "login");
        AuthRequest request = AuthRequest.fromJson(ctx.jsonBody());
        var response = authService.authenticate(request);
        return Response.ok(response.toMap());
    }

    private Response verifyEmail(RequestContext ctx) {
        limit(ctx, "verify-email");
        VerifyEmailRequest request = VerifyEmailRequest.fromJson(ctx.jsonBody());
        authService.verifyEmail(request.token);
        return Response.ok(message("Correo verificado correctamente. Ya puedes iniciar sesión."));
    }

    private Response resendVerification(RequestContext ctx) {
        limit(ctx, "resend-verification");
        ResendVerificationRequest request = ResendVerificationRequest.fromJson(ctx.jsonBody());
        authService.resendVerification(request.email);
        return Response.ok(message("Si el correo existe y no ha sido verificado, enviamos un nuevo enlace."));
    }

    private Response forgotPassword(RequestContext ctx) {
        limit(ctx, "forgot-password");
        ForgotPasswordRequest request = ForgotPasswordRequest.fromJson(ctx.jsonBody());
        authService.forgotPassword(request.email);
        return Response.ok(message("Si el correo existe, enviamos un enlace para restablecer la contraseña."));
    }

    private Response resetPassword(RequestContext ctx) {
        limit(ctx, "reset-password");
        ResetPasswordRequest request = ResetPasswordRequest.fromJson(ctx.jsonBody());
        authService.resetPassword(request.token, request.newPassword);
        return Response.ok(message("Contraseña actualizada correctamente. Ya puedes iniciar sesión."));
    }

    /** Lets the frontend bootstrap a session from a stored token without re-sending credentials. */
    private Response me(RequestContext ctx) {
        return Response.ok(authService.me(ctx.requireUserId()));
    }

    private Response updateMe(RequestContext ctx) {
        long userId = ctx.requireUserId();
        return Response.ok(authService.updateMe(userId, UpdateMeRequest.fromJson(ctx.jsonBody())));
    }

    private Response changePassword(RequestContext ctx) {
        long userId = ctx.requireUserId();
        limit(ctx, "change-password");
        authService.changePassword(userId, ChangePasswordRequest.fromJson(ctx.jsonBody()));
        return Response.ok(message("Contraseña actualizada correctamente."));
    }

    private void limit(RequestContext ctx, String route) {
        if (!rateLimiter.tryAcquire(route + ":" + ctx.clientIp())) {
            throw new TooManyRequestsException("Demasiados intentos. Intenta nuevamente en unos minutos.");
        }
    }

    private Map<String, Object> message(String text) {
        Map<String, Object> body = Json.obj();
        body.put("message", text);
        return body;
    }
}

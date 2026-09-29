package org.viajeseventos.controller;

import org.viajeseventos.dto.request.SendTestEmailRequest;
import org.viajeseventos.dto.request.SmtpSettingsRequest;
import org.viajeseventos.dto.response.SmtpSettingsResponse;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.http.Response;
import org.viajeseventos.http.Router;
import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.service.SmtpSettingsService;

import java.util.Map;

/** Outgoing SMTP provider (e.g. Maileroo), editable at runtime — requires the SETTINGS module. */
public final class SmtpSettingsController {

    private final SmtpSettingsService smtpSettingsService;

    public SmtpSettingsController(SmtpSettingsService smtpSettingsService) {
        this.smtpSettingsService = smtpSettingsService;
    }

    public void register(Router router) {
        router.get("/settings/smtp", AppModule.SETTINGS, this::get);
        router.put("/settings/smtp", AppModule.SETTINGS, this::update);
        router.post("/settings/smtp/test", AppModule.SETTINGS, this::sendTest);
    }

    private Response get(RequestContext ctx) {
        return Response.ok(response(smtpSettingsService.get()));
    }

    private Response update(RequestContext ctx) {
        return Response.ok(response(smtpSettingsService.update(SmtpSettingsRequest.fromJson(ctx.jsonBody()))));
    }

    private Response sendTest(RequestContext ctx) {
        SendTestEmailRequest request = SendTestEmailRequest.fromJson(ctx.jsonBody());
        smtpSettingsService.sendTest(request.to);
        Map<String, Object> body = Json.obj();
        body.put("message", "Correo de prueba enviado a " + request.to);
        return Response.ok(body);
    }

    private Map<String, Object> response(org.viajeseventos.model.SmtpSettings settings) {
        return new SmtpSettingsResponse(settings, smtpSettingsService.fallbackDescription()).toMap();
    }
}

package org.viajeseventos.controller;

import org.viajeseventos.dto.request.ProfileRequest;
import org.viajeseventos.dto.response.ProfileResponse;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.http.Response;
import org.viajeseventos.http.Router;
import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.service.ProfileService;

import java.util.Map;

/** Profile maintainer — every route requires the PROFILES module. */
public final class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void register(Router router) {
        // Before /profiles/{id} — routes match in registration order.
        router.get("/profiles/modules", AppModule.PROFILES, this::modules);
        router.get("/profiles", AppModule.PROFILES, this::list);
        router.get("/profiles/{id}", AppModule.PROFILES, this::get);
        router.post("/profiles", AppModule.PROFILES, this::create);
        router.put("/profiles/{id}", AppModule.PROFILES, this::update);
        router.delete("/profiles/{id}", AppModule.PROFILES, this::delete);
    }

    private Response modules(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("modules", AppModule.profileModules().stream().map(ProfileResponse::module).toList());
        return Response.ok(body);
    }

    private Response list(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("profiles", profileService.findAll().stream().map(ProfileResponse::from).toList());
        return Response.ok(body);
    }

    private Response get(RequestContext ctx) {
        return Response.ok(ProfileResponse.from(profileService.findById(ctx.pathParamLong("id"))));
    }

    private Response create(RequestContext ctx) {
        var created = profileService.create(ProfileRequest.fromJson(ctx.jsonBody()));
        return Response.created(ProfileResponse.from(profileService.findById(created.getId())));
    }

    private Response update(RequestContext ctx) {
        long id = ctx.pathParamLong("id");
        profileService.update(id, ProfileRequest.fromJson(ctx.jsonBody()));
        return Response.ok(ProfileResponse.from(profileService.findById(id)));
    }

    private Response delete(RequestContext ctx) {
        profileService.delete(ctx.pathParamLong("id"));
        return Response.noContent();
    }
}

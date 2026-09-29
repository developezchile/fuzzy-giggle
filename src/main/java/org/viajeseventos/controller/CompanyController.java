package org.viajeseventos.controller;

import org.viajeseventos.dto.request.CompanyRequest;
import org.viajeseventos.dto.response.CompanyResponse;
import org.viajeseventos.exception.ValidationException;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.http.Response;
import org.viajeseventos.http.Router;
import org.viajeseventos.json.Json;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.service.CompanyService;

import java.util.Map;

/**
 * Companies. {@code /companies/public/{slug}} is open (the client registration page); {@code /company}
 * is the caller's own company (COMPANY module); {@code /companies} is every company, for the
 * platform administrator (COMPANIES module).
 */
public final class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    public void register(Router router) {
        router.get("/companies/public/{slug}", this::publicView);

        router.get("/company", AppModule.COMPANY, this::mine);
        router.put("/company", AppModule.COMPANY, this::updateMine);

        router.get("/companies", AppModule.COMPANIES, this::list);
        router.patch("/companies/{id}", AppModule.COMPANIES, this::setActive);
    }

    private Response publicView(RequestContext ctx) {
        return Response.ok(CompanyResponse.publicView(companyService.findActiveBySlug(ctx.pathParam("slug"))));
    }

    private Response mine(RequestContext ctx) {
        return Response.ok(CompanyResponse.from(companyService.findById(ctx.caller().companyId())));
    }

    private Response updateMine(RequestContext ctx) {
        var company = companyService.update(ctx.caller().companyId(), CompanyRequest.fromJson(ctx.jsonBody()));
        return Response.ok(CompanyResponse.from(company));
    }

    private Response list(RequestContext ctx) {
        Map<String, Object> body = Json.obj();
        body.put("companies", companyService.findAll().stream().map(CompanyResponse::from).toList());
        return Response.ok(body);
    }

    private Response setActive(RequestContext ctx) {
        Object active = ctx.jsonBody().get("active");
        if (!(active instanceof Boolean value)) {
            throw new ValidationException(Map.of("active", "debe ser verdadero o falso"));
        }
        return Response.ok(CompanyResponse.from(companyService.setActive(ctx.caller(), ctx.pathParamLong("id"), value)));
    }
}

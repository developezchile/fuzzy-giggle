package org.viajeseventos.dto.response;

import org.viajeseventos.json.Json;
import org.viajeseventos.model.Company;
import org.viajeseventos.repository.CompanyRepository;

import java.util.Map;

public final class CompanyResponse {

    private CompanyResponse() {
    }

    /** What anyone may see — the client registration page shows it before there's an account. */
    public static Map<String, Object> publicView(Company company) {
        Map<String, Object> map = Json.obj();
        map.put("name", company.name());
        map.put("slug", company.slug());
        return map;
    }

    public static Map<String, Object> from(Company company) {
        Map<String, Object> map = publicView(company);
        map.put("id", company.id());
        map.put("contactEmail", company.contactEmail());
        map.put("active", company.active());
        map.put("createdAt", company.createdAt());
        return map;
    }

    public static Map<String, Object> from(CompanyRepository.Listing listing) {
        Map<String, Object> map = from(listing.company());
        map.put("userCount", listing.userCount());
        map.put("eventCount", listing.eventCount());
        return map;
    }
}

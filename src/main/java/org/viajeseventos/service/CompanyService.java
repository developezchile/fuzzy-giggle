package org.viajeseventos.service;

import org.viajeseventos.dto.request.CompanyRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.model.Company;
import org.viajeseventos.repository.CompanyRepository;
import org.viajeseventos.security.Caller;

import java.util.List;

/**
 * Companies: a company administrator's own one (COMPANY module), and the platform's list of all of
 * them (COMPANIES module). Signing a company up is {@link AuthService#registerCompany}.
 */
public final class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /** For the client registration page: only an active company's link works. */
    public Company findActiveBySlug(String slug) {
        return companyRepository.findBySlug(slug)
                .filter(Company::active)
                .orElseThrow(() -> new ResourceNotFoundException("La empresa del enlace no existe o está deshabilitada"));
    }

    public Company findById(long id) {
        return companyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada"));
    }

    public Company update(long companyId, CompanyRequest request) {
        findById(companyId);
        companyRepository.update(companyId, request.name, request.contactEmail);
        return findById(companyId);
    }

    public List<CompanyRepository.Listing> findAll() {
        return companyRepository.findAll();
    }

    /** Disabling locks every account of the company out (see ProfileRepository#findAccessByEnabledUserId). */
    public Company setActive(Caller caller, long id, boolean active) {
        findById(id);
        if (!active && id == caller.companyId()) {
            throw new BusinessRuleException("No puedes deshabilitar tu propia empresa");
        }
        companyRepository.setActive(id, active);
        return findById(id);
    }
}

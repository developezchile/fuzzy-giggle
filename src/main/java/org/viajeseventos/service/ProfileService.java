package org.viajeseventos.service;

import org.viajeseventos.dto.request.ProfileRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.DuplicateResourceException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.model.Profile;
import org.viajeseventos.repository.ProfileRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The profile maintainer (PROFILES module): which modules each profile grants. Rules, as in
 * condominios' ProfileService, plus a guard against locking everyone out:
 * <ul>
 *   <li>names are unique, case-insensitively;</li>
 *   <li>system profiles (ADMIN, CLIENT) can be renamed but never deleted;</li>
 *   <li>ADMIN always grants exactly {@link AppModule#adminModules()} — otherwise a company could be
 *       left with no one able to manage its users;</li>
 *   <li>platform modules are never granted through a profile (see {@link AppModule#platform()}) —
 *       any in the request are dropped;</li>
 *   <li>a profile still assigned to users can't be deleted.</li>
 * </ul>
 */
public final class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public List<Profile> findAll() {
        return profileRepository.findAll();
    }

    public Profile findById(long id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado"));
    }

    public Profile create(ProfileRequest request) {
        requireUniqueName(request.name, null);
        Profile profile = new Profile();
        profile.setName(request.name);
        profile.setDescription(request.description);
        profile.setModules(grantable(request.modules));
        return profileRepository.insert(profile);
    }

    public Profile update(long id, ProfileRequest request) {
        Profile profile = findById(id);
        requireUniqueName(request.name, id);
        Set<AppModule> modules = grantable(request.modules);
        if (profile.isAdmin()) {
            if (!modules.containsAll(AppModule.adminModules())) {
                throw new BusinessRuleException("El perfil Administrador siempre tiene todos los módulos de administración habilitados");
            }
            modules = AppModule.adminModules();
        }
        profile.setName(request.name);
        profile.setDescription(request.description);
        profile.setModules(modules);
        return profileRepository.update(profile);
    }

    public void delete(long id) {
        Profile profile = findById(id);
        if (profile.isSystem()) {
            throw new BusinessRuleException("No se puede eliminar un perfil del sistema");
        }
        if (profile.getUserCount() > 0) {
            throw new BusinessRuleException("El perfil tiene " + profile.getUserCount()
                    + " usuario(s) asignado(s). Asígnales otro perfil antes de eliminarlo.");
        }
        profileRepository.delete(id);
    }

    private static Set<AppModule> grantable(Set<AppModule> requested) {
        Set<AppModule> modules = EnumSet.noneOf(AppModule.class);
        modules.addAll(requested);
        modules.retainAll(AppModule.profileModules());
        return modules;
    }

    private void requireUniqueName(String name, Long excludeId) {
        if (profileRepository.existsByName(name, excludeId)) {
            throw new DuplicateResourceException("Ya existe un perfil con el nombre '" + name + "'");
        }
    }
}

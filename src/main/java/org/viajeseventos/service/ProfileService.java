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

/**
 * The profile maintainer (PROFILES module): which modules each profile grants. Rules, as in
 * condominios' ProfileService, plus a guard against locking everyone out:
 * <ul>
 *   <li>names are unique, case-insensitively;</li>
 *   <li>system profiles (ADMIN, CLIENT) can be renamed but never deleted;</li>
 *   <li>ADMIN always grants every module — otherwise removing PROFILES from it would leave no one
 *       able to undo that;</li>
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
        profile.setModules(request.modules);
        return profileRepository.insert(profile);
    }

    public Profile update(long id, ProfileRequest request) {
        Profile profile = findById(id);
        requireUniqueName(request.name, id);
        if (profile.isAdmin() && !request.modules.containsAll(EnumSet.allOf(AppModule.class))) {
            throw new BusinessRuleException("El perfil Administrador siempre tiene todos los módulos habilitados");
        }
        profile.setName(request.name);
        profile.setDescription(request.description);
        profile.setModules(request.modules);
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

    private void requireUniqueName(String name, Long excludeId) {
        if (profileRepository.existsByName(name, excludeId)) {
            throw new DuplicateResourceException("Ya existe un perfil con el nombre '" + name + "'");
        }
    }
}

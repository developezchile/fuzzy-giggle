package org.viajeseventos.security;

import org.viajeseventos.exception.ForbiddenException;
import org.viajeseventos.http.RequestContext;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.repository.ProfileRepository;

/**
 * Server-side enforcement of module access — the counterpart of condominios'
 * {@code @RequiresModule} + {@code ModuleAccessInterceptor}. The {@link org.viajeseventos.http.Router}
 * calls this before any route registered with an {@link AppModule}: the caller must be logged in,
 * enabled, and their profile must grant the module. The UI hiding a nav item is a convenience,
 * this is the actual access control.
 */
public final class ModuleAccess {

    private final ProfileRepository profileRepository;

    public ModuleAccess(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /** Returns the authenticated user id, or throws 401 (no/invalid token) / 403 (module not granted). */
    public long require(RequestContext ctx, AppModule module) {
        long userId = ctx.requireUserId();
        if (!profileRepository.findModulesByEnabledUserId(userId).contains(module)) {
            throw new ForbiddenException("Tu perfil no tiene acceso al módulo " + module.label());
        }
        return userId;
    }
}

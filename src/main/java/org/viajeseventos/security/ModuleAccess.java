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

    /** Returns the caller, or throws 401 (no/invalid token) / 403 (module disabled or not granted). */
    public Caller require(RequestContext ctx, AppModule module) {
        long userId = ctx.requireUserId();
        if (module.disabled()) {
            throw new ForbiddenException("El módulo " + module.label() + " no está disponible");
        }
        ProfileRepository.Access access = profileRepository.findAccessByEnabledUserId(userId).orElse(null);
        if (access == null || !access.modules().contains(module)) {
            throw new ForbiddenException("Tu perfil no tiene acceso al módulo " + module.label());
        }
        return new Caller(userId, access.companyId());
    }
}

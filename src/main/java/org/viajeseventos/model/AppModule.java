package org.viajeseventos.model;

import java.util.Optional;

/**
 * The feature areas a {@link Profile} can grant. Each API route that belongs to a module is
 * registered with it ({@code router.get(path, AppModule.X, handler)}) and the frontend shows the
 * module's pages only when the caller's profile grants it — so what a profile can do is edited as
 * data from the profile maintainer, not in code. Keys are persisted in {@code profile_modules}.
 *
 * <p>Adding a module: add it here, tag its routes, add its nav entry in the UI. The ADMIN profile
 * picks it up automatically at the next startup (see {@code AdminBootstrap}).
 */
public enum AppModule {
    EVENTS("Eventos", "Ver eventos, reservar viajes y ver mis reservas"),
    BOOKINGS("Reservas", "Ver todas las reservas y pasajeros por evento"),
    EVENT_ADMIN("Gestión de eventos", "Crear, editar, activar y eliminar eventos"),
    USERS("Usuarios", "Administrar cuentas y asignarles un perfil"),
    PROFILES("Perfiles", "Crear perfiles y habilitar sus módulos"),
    SETTINGS("Configuración", "Configurar el servidor de correo (SMTP)");

    private final String label;
    private final String description;

    AppModule(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Lenient lookup for keys read back from the database — a key whose module was since removed from code is skipped, not fatal. */
    public static Optional<AppModule> fromKey(String key) {
        for (AppModule module : values()) {
            if (module.name().equals(key)) return Optional.of(module);
        }
        return Optional.empty();
    }
}

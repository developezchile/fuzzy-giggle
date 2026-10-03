package org.viajeseventos.model;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The feature areas a {@link Profile} can grant. Each API route that belongs to a module is
 * registered with it ({@code router.get(path, AppModule.X, handler)}) and the frontend shows the
 * module's pages only when the caller's profile grants it — so what a profile can do is edited as
 * data from the profile maintainer, not in code. Keys are persisted in {@code profile_modules}.
 *
 * <p>Adding a module: add it here, tag its routes, add its nav entry in the UI. The ADMIN profile
 * picks it up automatically at the next startup (see {@code AdminBootstrap}).
 *
 * <p>Platform modules ({@link #platform()}) manage what every company shares — profiles, outgoing
 * mail, the companies themselves. They're never granted through a profile (profiles are shared by
 * all companies, so a company administrator could otherwise grant them): only accounts flagged
 * {@code platform_admin} get them.
 */
public enum AppModule {
    EVENTS("Eventos", "Ver los eventos próximos", false),
    MY_BOOKINGS("Mis reservas", "Reservar viajes a los eventos y ver o cancelar mis reservas", false),
    BOOKINGS("Reservas", "Ver todas las reservas y pasajeros por evento", false),
    EVENT_ADMIN("Gestión de eventos", "Crear, editar, activar y eliminar eventos", false),
    TRIP_ADMIN("Salidas", "Publicar las salidas a cada evento con su cupo, precio y paradas", false),
    FARES("Recorridos y tarifas", "Mantener los recorridos con sus paradas y precios, y los buses de la empresa", false),
    BOARDING("Embarque", "Manifiesto del conductor y check-in de los pasajeros al subir", false),
    REVIEWS("Reseñas", "Ver y moderar las reseñas de las salidas, y editar las políticas de la empresa", false),
    USERS("Usuarios", "Administrar las cuentas de la empresa y asignarles un perfil", false),
    COMPANY("Mi empresa", "Datos de la empresa y link de registro para sus clientes", false),
    PROFILES("Perfiles", "Crear perfiles y habilitar sus módulos", true),
    SETTINGS("Configuración", "Configurar el servidor de correo (SMTP)", true),
    COMPANIES("Empresas", "Ver las empresas de la plataforma y habilitarlas o deshabilitarlas", true);

    /**
     * Switched off for now: {@code ModuleAccess} refuses their routes to everyone, whatever the
     * profile, and the UI hides their pages. A super admin will get them in a later version.
     */
    private static final Set<AppModule> DISABLED = EnumSet.of(COMPANY, PROFILES, SETTINGS, COMPANIES);

    private final String label;
    private final String description;
    private final boolean platform;

    AppModule(String label, String description, boolean platform) {
        this.label = label;
        this.description = description;
        this.platform = platform;
    }

    /** Granted only to platform administrators, never through a profile. */
    public boolean platform() {
        return platform;
    }

    /** See {@link #DISABLED}. */
    public boolean disabled() {
        return DISABLED.contains(this);
    }

    /** The modules a profile can grant. */
    public static Set<AppModule> profileModules() {
        Set<AppModule> modules = EnumSet.noneOf(AppModule.class);
        for (AppModule module : values()) {
            if (!module.platform) modules.add(module);
        }
        return modules;
    }

    /**
     * The modules the ADMIN profile always has: every profile module except {@link #MY_BOOKINGS} —
     * administrators don't book trips for now.
     */
    public static Set<AppModule> adminModules() {
        Set<AppModule> modules = profileModules();
        modules.remove(MY_BOOKINGS);
        return modules;
    }

    public static Set<AppModule> platformModules() {
        Set<AppModule> modules = EnumSet.noneOf(AppModule.class);
        for (AppModule module : values()) {
            if (module.platform) modules.add(module);
        }
        return modules;
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

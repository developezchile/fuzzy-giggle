package org.viajeseventos;

import org.viajeseventos.config.Env;
import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.model.Profile;
import org.viajeseventos.model.User;
import org.viajeseventos.repository.CompanyRepository;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.security.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Runs once at startup, after migrations:
 * <ol>
 *   <li>re-grants the ADMIN profile its {@link AppModule#adminModules()}, so a module added in code
 *       reaches every company's administrators without a migration;</li>
 *   <li>creates the platform administrator if none exists — an ADMIN of the first company
 *       (V5__companies.sql) flagged {@code platform_admin}, with {@code ADMIN_BOOTSTRAP_PASSWORD} if
 *       set, otherwise a freshly generated password logged once. No credential ever ships in git.</li>
 * </ol>
 */
public final class AdminBootstrap {

    private static final Logger log = LogManager.getLogger(AdminBootstrap.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private AdminBootstrap() {
    }

    /** Slug of the company seeded by V5__companies.sql — home of the platform administrator. */
    static final String PLATFORM_COMPANY_SLUG = "viajes-eventos";

    public static void run(ProfileRepository profileRepository, UserRepository userRepository,
                           CompanyRepository companyRepository, PasswordEncoder passwordEncoder) {
        Profile admin = profileRepository.findByCode(Profile.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN profile missing — V1__init.sql seeds it"));
        syncAdminModules(admin, profileRepository);
        if (!userRepository.existsPlatformAdmin()) {
            long companyId = companyRepository.findBySlug(PLATFORM_COMPANY_SLUG)
                    .orElseThrow(() -> new IllegalStateException("Company '" + PLATFORM_COMPANY_SLUG + "' missing — V5__companies.sql seeds it"))
                    .id();
            createAdmin(admin, companyId, userRepository, passwordEncoder);
        }
    }

    private static void syncAdminModules(Profile admin, ProfileRepository profileRepository) {
        Set<AppModule> modules = AppModule.adminModules();
        if (admin.getModules().equals(modules)) return;
        admin.setModules(modules);
        profileRepository.update(admin);
        log.info("Synced the ADMIN profile's modules: {}", modules);
    }

    private static void createAdmin(Profile adminProfile, long companyId, UserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        String password = Env.get("ADMIN_BOOTSTRAP_PASSWORD", "");
        boolean generated = password.isBlank();
        if (generated) {
            password = randomPassword();
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail(Env.get("ADMIN_BOOTSTRAP_EMAIL", "admin@viajeseventos.local"));
        admin.setFirstName("Administrador");
        admin.setProfileId(adminProfile.getId());
        admin.setCompanyId(companyId);
        admin.setPlatformAdmin(true);
        admin.setEnabled(true);
        admin.setEmailVerified(true);
        admin.setPassword(passwordEncoder.encode(password));
        userRepository.insert(admin);

        if (generated) {
            log.warn("=== Admin bootstrap ====================================================");
            log.warn("Created the administrator account with a freshly generated password (no default is "
                    + "ever shipped). email={} password={}", admin.getEmail(), password);
            log.warn("This password is shown ONLY here, ONLY once — store it now. Set "
                    + "ADMIN_BOOTSTRAP_PASSWORD to control it explicitly instead.");
            log.warn("=========================================================================");
        } else {
            log.info("Created the administrator account {} using ADMIN_BOOTSTRAP_PASSWORD.", admin.getEmail());
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

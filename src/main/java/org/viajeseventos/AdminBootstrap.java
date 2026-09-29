package org.viajeseventos;

import org.viajeseventos.config.Env;
import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.model.Profile;
import org.viajeseventos.model.User;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.security.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;

/**
 * Runs once at startup, after migrations:
 * <ol>
 *   <li>re-grants every {@link AppModule} to the ADMIN profile, so a module added in code reaches
 *       administrators without a migration;</li>
 *   <li>creates an administrator account if none exists — with {@code ADMIN_BOOTSTRAP_PASSWORD} if
 *       set, otherwise a freshly generated password logged once. No credential ever ships in git.</li>
 * </ol>
 */
public final class AdminBootstrap {

    private static final Logger log = LogManager.getLogger(AdminBootstrap.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private AdminBootstrap() {
    }

    public static void run(ProfileRepository profileRepository, UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        Profile admin = profileRepository.findByCode(Profile.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN profile missing — V1__init.sql seeds it"));
        syncAdminModules(admin, profileRepository);
        if (!userRepository.existsByProfileId(admin.getId())) {
            createAdmin(admin, userRepository, passwordEncoder);
        }
    }

    private static void syncAdminModules(Profile admin, ProfileRepository profileRepository) {
        EnumSet<AppModule> all = EnumSet.allOf(AppModule.class);
        if (admin.getModules().equals(all)) return;
        admin.setModules(all);
        profileRepository.update(admin);
        log.info("Granted every module to the ADMIN profile: {}", all);
    }

    private static void createAdmin(Profile adminProfile, UserRepository userRepository, PasswordEncoder passwordEncoder) {
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

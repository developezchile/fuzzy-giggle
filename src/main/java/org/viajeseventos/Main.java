package org.viajeseventos;

import org.viajeseventos.config.AppConfig;
import org.viajeseventos.controller.AuthController;
import org.viajeseventos.controller.BookingController;
import org.viajeseventos.controller.EventAdminController;
import org.viajeseventos.controller.HealthController;
import org.viajeseventos.controller.ProfileController;
import org.viajeseventos.controller.SmtpSettingsController;
import org.viajeseventos.controller.UserController;
import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.db.MigrationRunner;
import org.viajeseventos.email.ConfigurableEmailSender;
import org.viajeseventos.email.EmailSender;
import org.viajeseventos.email.LoggingEmailSender;
import org.viajeseventos.email.SmtpEmailSender;
import org.viajeseventos.http.Router;
import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;
import org.viajeseventos.repository.BookingRepository;
import org.viajeseventos.repository.EmailVerificationTokenRepository;
import org.viajeseventos.repository.EventRepository;
import org.viajeseventos.repository.PasswordResetTokenRepository;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.SmtpSettingsRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.security.JwtService;
import org.viajeseventos.security.ModuleAccess;
import org.viajeseventos.security.PasswordEncoder;
import org.viajeseventos.security.RateLimiter;
import org.viajeseventos.service.AuthService;
import org.viajeseventos.service.BookingService;
import org.viajeseventos.service.EventService;
import org.viajeseventos.service.ProfileService;
import org.viajeseventos.service.SmtpSettingsService;
import org.viajeseventos.service.UserService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entry point. Wires controller -> service -> repository by hand (no DI framework) and starts a
 * plain JDK {@link HttpServer}. Covers auth (register/login/email verification/password reset),
 * events and bus bookings, the profile maintainer and account administration — access to each feature is granted per
 * profile through modules (condominios' pattern) — mirroring the "library-free" architecture proven in dc-api-v2: no
 * Spring/Jackson/Hibernate/JJWT, just the JDK standard library plus a JDBC driver, a small
 * bcrypt implementation, and Jakarta Mail for SMTP.
 */
public final class Main {

    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Banner.print();

        AppConfig config = new AppConfig();

        ConnectionPool pool = new ConnectionPool(config.dbUrl, config.dbUsername, config.dbPassword, config.dbPoolSize);
        new MigrationRunner(pool).migrate();

        ProfileRepository profileRepository = new ProfileRepository(pool);
        UserRepository userRepository = new UserRepository(pool);
        EmailVerificationTokenRepository emailVerificationTokenRepository = new EmailVerificationTokenRepository(pool);
        PasswordResetTokenRepository passwordResetTokenRepository = new PasswordResetTokenRepository(pool);

        PasswordEncoder passwordEncoder = new PasswordEncoder();
        AdminBootstrap.run(profileRepository, userRepository, passwordEncoder);

        JwtService jwtService = new JwtService(config.jwtSecret, config.jwtExpirationMs);

        // Settings saved from the SETTINGS module win (checked on every send); SMTP_* from env/config.yml
        // is the fallback, and with neither, emails are only logged.
        EmailSender configuredSender = config.smtpHost.isBlank()
                ? new LoggingEmailSender()
                : new SmtpEmailSender(config.smtpHost, config.smtpPort, config.smtpUsername, config.smtpPassword,
                        config.smtpStartTls, config.smtpFromAddress, config.smtpFromName);
        ConfigurableEmailSender emailSender = new ConfigurableEmailSender(new SmtpSettingsRepository(pool),
                configuredSender, config.smtpHost.isBlank() ? null : config.smtpHost + ":" + config.smtpPort);

        RateLimiter authRateLimiter = new RateLimiter(config.rateLimitMaxRequests, config.rateLimitWindowMs);

        AuthService authService = new AuthService(userRepository, profileRepository, emailVerificationTokenRepository,
                passwordResetTokenRepository, passwordEncoder, jwtService, emailSender, config.frontendUrl);
        AuthController authController = new AuthController(authService, authRateLimiter);

        HealthController healthController = new HealthController(pool);
        SmtpSettingsController smtpSettingsController = new SmtpSettingsController(
                new SmtpSettingsService(new SmtpSettingsRepository(pool), emailSender));

        ProfileController profileController = new ProfileController(new ProfileService(profileRepository));
        // Chile time: an event stays bookable through its last local day.
        EventRepository eventRepository = new EventRepository(pool);
        BookingService bookingService = new BookingService(eventRepository, new BookingRepository(pool),
                Clock.system(ZoneId.of("America/Santiago")));
        BookingController bookingController = new BookingController(bookingService);
        EventAdminController eventAdminController = new EventAdminController(new EventService(eventRepository));
        UserController userController = new UserController(
                new UserService(userRepository, profileRepository, passwordEncoder), authService);

        List<String> allowedOrigins = List.of(config.frontendUrl,
                "http://localhost:3000", "http://localhost:4200", "http://localhost:5173", "http://localhost:8080");
        Router router = new Router(jwtService, new ModuleAccess(profileRepository), allowedOrigins, config.contextPath);
        healthController.register(router);
        authController.register(router);
        bookingController.register(router);
        eventAdminController.register(router);
        profileController.register(router);
        userController.register(router);
        smtpSettingsController.register(router);

        ScheduledExecutorService rateLimiterCleanup = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "rate-limiter-cleanup"));
        rateLimiterCleanup.scheduleAtFixedRate(authRateLimiter::evictExpired, 5, 5, TimeUnit.MINUTES);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            rateLimiterCleanup.shutdownNow();
            pool.close();
        }));

        log.info("viajes-eventos-api listening on http://localhost:{}{}", config.port, config.contextPath);
    }
}

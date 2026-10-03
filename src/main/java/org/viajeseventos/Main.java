package org.viajeseventos;

import org.viajeseventos.config.AppConfig;
import org.viajeseventos.controller.AuthController;
import org.viajeseventos.controller.BoardingController;
import org.viajeseventos.controller.BookingController;
import org.viajeseventos.controller.CompanyController;
import org.viajeseventos.controller.EventAdminController;
import org.viajeseventos.controller.HealthController;
import org.viajeseventos.controller.ProfileController;
import org.viajeseventos.controller.PublicController;
import org.viajeseventos.controller.ReviewController;
import org.viajeseventos.controller.SmtpSettingsController;
import org.viajeseventos.controller.RouteController;
import org.viajeseventos.controller.TripAdminController;
import org.viajeseventos.controller.UserController;
import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.db.MigrationRunner;
import org.viajeseventos.email.ConfigurableEmailSender;
import org.viajeseventos.email.EmailSender;
import org.viajeseventos.email.LoggingEmailSender;
import org.viajeseventos.email.SmtpEmailSender;
import org.viajeseventos.notify.EmailNotificationSender;
import org.viajeseventos.notify.NotificationSender;
import org.viajeseventos.notify.TripNotifier;
import org.viajeseventos.http.Router;
import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;
import org.viajeseventos.repository.BookingRepository;
import org.viajeseventos.repository.BusTypeRepository;
import org.viajeseventos.repository.CompanyRepository;
import org.viajeseventos.repository.EmailVerificationTokenRepository;
import org.viajeseventos.repository.EventRepository;
import org.viajeseventos.repository.RouteRepository;
import org.viajeseventos.repository.PasswordResetTokenRepository;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.ReviewRepository;
import org.viajeseventos.repository.SmtpSettingsRepository;
import org.viajeseventos.repository.TripRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.repository.WaitlistRepository;
import org.viajeseventos.security.JwtService;
import org.viajeseventos.security.ModuleAccess;
import org.viajeseventos.security.PasswordEncoder;
import org.viajeseventos.security.RateLimiter;
import org.viajeseventos.service.RouteService;
import org.viajeseventos.service.AuthService;
import org.viajeseventos.service.BoardingService;
import org.viajeseventos.service.BookingService;
import org.viajeseventos.service.CompanyService;
import org.viajeseventos.service.EventService;
import org.viajeseventos.service.ProfileService;
import org.viajeseventos.service.PublicCatalogService;
import org.viajeseventos.service.ReviewService;
import org.viajeseventos.service.SmtpSettingsService;
import org.viajeseventos.service.TripReminderJob;
import org.viajeseventos.service.TripService;
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
 * plain JDK {@link HttpServer}. Multi-company: each transport company has its own administrators,
 * clients and events. Covers auth (company sign-up, client sign-up through the company's link,
 * login/email verification/password reset), events and bus bookings, the profile maintainer and account administration — access to each feature is granted per
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
        CompanyRepository companyRepository = new CompanyRepository(pool);
        UserRepository userRepository = new UserRepository(pool);
        EmailVerificationTokenRepository emailVerificationTokenRepository = new EmailVerificationTokenRepository(pool);
        PasswordResetTokenRepository passwordResetTokenRepository = new PasswordResetTokenRepository(pool);

        PasswordEncoder passwordEncoder = new PasswordEncoder();
        AdminBootstrap.run(profileRepository, userRepository, companyRepository, passwordEncoder);

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

        AuthService authService = new AuthService(userRepository, profileRepository, companyRepository, emailVerificationTokenRepository,
                passwordResetTokenRepository, passwordEncoder, jwtService, emailSender, config.frontendUrl);
        AuthController authController = new AuthController(authService, authRateLimiter);

        HealthController healthController = new HealthController(pool);
        SmtpSettingsController smtpSettingsController = new SmtpSettingsController(
                new SmtpSettingsService(new SmtpSettingsRepository(pool), emailSender));

        ProfileController profileController = new ProfileController(new ProfileService(profileRepository));

        // Chile time: an event stays bookable through its last local day, and a departure's
        // deadline is read in the same zone.
        Clock clock = Clock.system(ZoneId.of("America/Santiago"));
        EventRepository eventRepository = new EventRepository(pool);
        TripRepository tripRepository = new TripRepository(pool);
        WaitlistRepository waitlistRepository = new WaitlistRepository(pool);
        BookingRepository bookingRepository = new BookingRepository(pool);
        // Everything a trip tells a passenger goes through here; email is the only channel wired.
        NotificationSender notifications = new EmailNotificationSender(emailSender);
        TripNotifier tripNotifier = new TripNotifier(companyRepository, notifications, config.frontendUrl);

        RouteService routeService = new RouteService(new RouteRepository(pool), new BusTypeRepository(pool));
        BookingService bookingService = new BookingService(tripRepository, bookingRepository,
                waitlistRepository, tripNotifier, clock);
        TripService tripService = new TripService(tripRepository, eventRepository, waitlistRepository,
                bookingRepository, tripNotifier, routeService);
        BoardingService boardingService = new BoardingService(tripRepository, bookingRepository, new BusTypeRepository(pool), clock);
        RouteController routeController = new RouteController(routeService);
        BookingController bookingController = new BookingController(bookingService);
        TripAdminController tripAdminController = new TripAdminController(tripService, bookingService);
        BoardingController boardingController = new BoardingController(boardingService);
        ReviewRepository reviewRepository = new ReviewRepository(pool);
        ReviewController reviewController = new ReviewController(new ReviewService(reviewRepository,
                bookingRepository, tripRepository, companyRepository, clock));
        // The public, indexable catalog: no module, no token. One deployment serves one company.
        PublicController publicController = new PublicController(new PublicCatalogService(companyRepository,
                eventRepository, tripRepository, reviewRepository, config.companySlug, clock));
        EventAdminController eventAdminController = new EventAdminController(new EventService(eventRepository));
        CompanyController companyController = new CompanyController(new CompanyService(companyRepository));
        UserController userController = new UserController(
                new UserService(userRepository, profileRepository, passwordEncoder), authService);

        // FRONTEND_URL is always allowed; CORS_ORIGINS adds more (e.g. a custom domain next to the
        // onrender.com one) without hardcoding them here.
        List<String> allowedOrigins = new java.util.ArrayList<>(List.of(config.frontendUrl,
                "http://localhost:3000", "http://localhost:4200", "http://localhost:5173", "http://localhost:8080"));
        allowedOrigins.addAll(config.corsOrigins);
        log.info("CORS allowed origins: {}", allowedOrigins);
        Router router = new Router(jwtService, new ModuleAccess(profileRepository), allowedOrigins, config.contextPath);
        healthController.register(router);
        authController.register(router);
        bookingController.register(router);
        tripAdminController.register(router);
        routeController.register(router);
        boardingController.register(router);
        reviewController.register(router);
        publicController.register(router);
        eventAdminController.register(router);
        profileController.register(router);
        userController.register(router);
        companyController.register(router);
        smtpSettingsController.register(router);

        ScheduledExecutorService rateLimiterCleanup = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "rate-limiter-cleanup"));
        rateLimiterCleanup.scheduleAtFixedRate(authRateLimiter::evictExpired, 5, 5, TimeUnit.MINUTES);

        // The day-before reminder. The interval only decides how soon a trip entering the 24-hour
        // window hears about it: trips.reminder_sent_at is what keeps it to one message each.
        ScheduledExecutorService reminders = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "trip-reminders"));
        reminders.scheduleAtFixedRate(new TripReminderJob(tripRepository, bookingRepository, tripNotifier, clock),
                1, 30, TimeUnit.MINUTES);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            rateLimiterCleanup.shutdownNow();
            reminders.shutdownNow();
            pool.close();
        }));

        log.info("viajes-eventos-api listening on http://localhost:{}{}", config.port, config.contextPath);
    }
}

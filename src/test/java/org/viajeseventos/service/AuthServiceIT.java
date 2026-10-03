package org.viajeseventos.service;

import org.viajeseventos.AdminBootstrap;
import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.dto.request.AuthRequest;
import org.viajeseventos.dto.request.RegisterCompanyRequest;
import org.viajeseventos.dto.request.RegisterRequest;
import org.viajeseventos.dto.response.AuthResponse;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.DuplicateResourceException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.exception.UnauthorizedException;
import org.viajeseventos.model.Company;
import org.viajeseventos.repository.CompanyRepository;
import org.viajeseventos.repository.EmailVerificationTokenRepository;
import org.viajeseventos.repository.PasswordResetTokenRepository;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.security.JwtService;
import org.viajeseventos.security.PasswordEncoder;
import org.viajeseventos.testsupport.FakeEmailSender;
import org.viajeseventos.testsupport.TestDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises AuthService's registration/verification/reset flows against a real local Postgres
 * (see {@link TestDb}), with a {@link FakeEmailSender} standing in for SMTP so tests can read the
 * verification/reset token straight out of the "sent" email instead of needing a mail server.
 */
class AuthServiceIT {

    private static ConnectionPool pool;
    private static AuthService authService;
    private static FakeEmailSender emailSender;
    private static UserRepository userRepository;
    private static CompanyRepository companyRepository;
    /** The slug a client's registration link carries — read from the database, not hardcoded. */
    private static String companySlug;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        userRepository = new UserRepository(pool);
        companyRepository = new CompanyRepository(pool);
        // As at app startup: ADMIN gets every profile module (and a platform admin exists).
        AdminBootstrap.run(new ProfileRepository(pool), userRepository, companyRepository, new PasswordEncoder());
        companySlug = TestDb.seededCompanySlug(pool);
        EmailVerificationTokenRepository evtRepo = new EmailVerificationTokenRepository(pool);
        PasswordResetTokenRepository prtRepo = new PasswordResetTokenRepository(pool);
        emailSender = new FakeEmailSender();
        authService = new AuthService(userRepository, new ProfileRepository(pool), companyRepository, evtRepo, prtRepo, new PasswordEncoder(),
                new JwtService("dGVzdC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0cy1vbmx5", 3_600_000), emailSender,
                "http://localhost:3000");
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    private RegisterRequest registerRequest(String username, String email) {
        return RegisterRequest.fromJson(Map.of(
                "username", username, "email", email, "password", "Password123!", "company", companySlug));
    }

    private RegisterCompanyRequest registerCompanyRequest(String companyName, String email) {
        return RegisterCompanyRequest.fromJson(Map.of("companyName", companyName,
                "username", "co" + suffix(email), "email", email, "password", "Password123!"));
    }

    private AuthResponse verifyAndLogin(String email) {
        authService.verifyEmail(emailSender.lastTokenSentTo(email));
        return authService.authenticate(AuthRequest.fromJson(Map.of("email", email, "password", "Password123!")));
    }

    @Test
    void aSignedUpCompanyGetsItsAdministratorAndItsOwnRegistrationLink() {
        String email = uniqueEmail("company");
        String name = "Buses López " + System.nanoTime();
        Company company = authService.registerCompany(registerCompanyRequest(name, email));

        assertTrue(company.slug().startsWith("buses-lopez-"), company.slug());
        assertEquals(email, company.contactEmail());
        // The verification email goes out in the company's name, replies to it.
        assertEquals(name, emailSender.lastSentTo(email).onBehalfOf().name());
        assertEquals(email, emailSender.lastSentTo(email).onBehalfOf().replyTo());

        AuthResponse admin = verifyAndLogin(email);
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) admin.user.get("profile");
        assertEquals("ADMIN", profile.get("code"));
        @SuppressWarnings("unchecked")
        java.util.List<String> modules = (java.util.List<String>) admin.user.get("modules");
        assertTrue(modules.containsAll(java.util.List.of("EVENTS", "BOOKINGS", "EVENT_ADMIN", "USERS", "COMPANY")));
        // A company administrator is not the platform's: no profiles, SMTP or companies list.
        assertFalse(modules.contains("PROFILES") || modules.contains("SETTINGS") || modules.contains("COMPANIES"));
        // Administrators don't book trips (for now).
        assertFalse(modules.contains("MY_BOOKINGS"));

        // Clients registering through the link land in that company.
        String clientEmail = uniqueEmail("companyclient");
        authService.register(RegisterRequest.fromJson(Map.of("username", "cc" + suffix(clientEmail),
                "email", clientEmail, "password", "Password123!", "company", company.slug())));
        assertEquals(company.id(), userRepository.findByEmail(clientEmail).orElseThrow().getCompanyId());
    }

    @Test
    void aTakenCompanyLinkIsRejected() {
        String slug = "link-" + System.nanoTime();
        authService.registerCompany(RegisterCompanyRequest.fromJson(Map.of("companyName", "Uno", "companySlug", slug,
                "username", "u1" + slug, "email", uniqueEmail("slug1"), "password", "Password123!")));
        assertThrows(DuplicateResourceException.class, () -> authService.registerCompany(RegisterCompanyRequest.fromJson(
                Map.of("companyName", "Dos", "companySlug", slug,
                        "username", "u2" + slug, "email", uniqueEmail("slug2"), "password", "Password123!"))));
    }

    @Test
    void registeringThroughAnUnknownOrDisabledCompanyLinkFails() {
        String email = uniqueEmail("nolink");
        assertThrows(ResourceNotFoundException.class, () -> authService.register(RegisterRequest.fromJson(Map.of(
                "username", "nl" + suffix(email), "email", email, "password", "Password123!", "company", "no-existe-xyz"))));

        long disabled = TestDb.newCompanyId(pool);
        companyRepository.setActive(disabled, false);
        String slug = companyRepository.findById(disabled).orElseThrow().slug();
        assertThrows(ResourceNotFoundException.class, () -> authService.register(RegisterRequest.fromJson(Map.of(
                "username", "nl" + suffix(email), "email", email, "password", "Password123!", "company", slug))));
    }

    @Test
    void disablingACompanyLocksItsAccountsOut() {
        String email = uniqueEmail("locked");
        Company company = authService.registerCompany(registerCompanyRequest("Bloqueada " + System.nanoTime(), email));
        verifyAndLogin(email);

        companyRepository.setActive(company.id(), false);

        AuthRequest login = AuthRequest.fromJson(Map.of("email", email, "password", "Password123!"));
        assertTrue(assertThrows(BusinessRuleException.class, () -> authService.authenticate(login))
                .getMessage().contains("empresa"));
        long userId = userRepository.findByEmail(email).orElseThrow().getId();
        assertThrows(UnauthorizedException.class, () -> authService.me(userId));
    }

    @Test
    void registeredUserCannotLoginUntilVerified() {
        String email = uniqueEmail("unverified");
        authService.register(registerRequest("unverified" + suffix(email), email));

        AuthRequest login = AuthRequest.fromJson(Map.of("email", email, "password", "Password123!"));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> authService.authenticate(login));
        assertTrue(ex.getMessage().toLowerCase().contains("verificar"));
    }

    @Test
    void verifyingWithTheEmailedTokenUnlocksLogin() {
        String email = uniqueEmail("verifyme");
        authService.register(registerRequest("verifyme" + suffix(email), email));
        String token = emailSender.lastTokenSentTo(email);

        authService.verifyEmail(token);

        AuthRequest login = AuthRequest.fromJson(Map.of("email", email, "password", "Password123!"));
        AuthResponse response = authService.authenticate(login);
        assertEquals(email, response.user.get("email"));
    }

    @Test
    void selfRegisteredUserGetsTheClientProfileAndItsModules() {
        String email = uniqueEmail("client");
        authService.register(registerRequest("client" + suffix(email), email));
        authService.verifyEmail(emailSender.lastTokenSentTo(email));

        AuthResponse response = authService.authenticate(
                AuthRequest.fromJson(Map.of("email", email, "password", "Password123!")));

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) response.user.get("profile");
        assertEquals("CLIENT", profile.get("code"));
        assertEquals(java.util.List.of("EVENTS", "MY_BOOKINGS"), response.user.get("modules"));
    }

    @Test
    void adminCanMarkAnEmailVerifiedWhichUnlocksLoginAndVoidsTheLink() {
        String email = uniqueEmail("adminverify");
        authService.register(registerRequest("adminverify" + suffix(email), email));
        String pendingToken = emailSender.lastTokenSentTo(email);
        long userId = userRepository.findByEmail(email).orElseThrow().getId();

        authService.markEmailVerified(userId);

        AuthResponse response = authService.authenticate(
                AuthRequest.fromJson(Map.of("email", email, "password", "Password123!")));
        assertEquals(email, response.user.get("email"));
        assertThrows(BusinessRuleException.class, () -> authService.verifyEmail(pendingToken));
        assertThrows(BusinessRuleException.class, () -> authService.markEmailVerified(userId));
        assertThrows(BusinessRuleException.class, () -> authService.resendVerificationTo(userId));
    }

    @Test
    void adminCanResendTheVerificationLink() {
        String email = uniqueEmail("adminresend");
        authService.register(registerRequest("adminresend" + suffix(email), email));
        String firstToken = emailSender.lastTokenSentTo(email);
        long userId = userRepository.findByEmail(email).orElseThrow().getId();

        authService.resendVerificationTo(userId);

        String secondToken = emailSender.lastTokenSentTo(email);
        assertNotEquals(firstToken, secondToken);
        authService.verifyEmail(secondToken);
    }

    @Test
    void verificationTokenCannotBeReused() {
        String email = uniqueEmail("reuse");
        authService.register(registerRequest("reuse" + suffix(email), email));
        String token = emailSender.lastTokenSentTo(email);

        authService.verifyEmail(token);

        assertThrows(BusinessRuleException.class, () -> authService.verifyEmail(token));
    }

    @Test
    void duplicateUsernameOrEmailIsRejected() {
        String email = uniqueEmail("dup");
        authService.register(registerRequest("dupuser" + suffix(email), email));

        assertThrows(DuplicateResourceException.class,
                () -> authService.register(registerRequest("dupuser" + suffix(email), uniqueEmail("dup2"))));
        assertThrows(DuplicateResourceException.class,
                () -> authService.register(registerRequest("someoneElse" + suffix(email), email)));
    }

    @Test
    void forgotPasswordThenResetChangesThePassword() {
        String email = uniqueEmail("reset");
        authService.register(registerRequest("reset" + suffix(email), email));
        authService.verifyEmail(emailSender.lastTokenSentTo(email));

        authService.forgotPassword(email);
        String resetToken = emailSender.lastTokenSentTo(email);
        authService.resetPassword(resetToken, "NewPassword456!");

        assertThrows(BusinessRuleException.class, () -> authService.authenticate(
                AuthRequest.fromJson(Map.of("email", email, "password", "Password123!"))));
        AuthResponse response = authService.authenticate(
                AuthRequest.fromJson(Map.of("email", email, "password", "NewPassword456!")));
        assertEquals(email, response.user.get("email"));
    }

    @Test
    void resetTokenCannotBeReused() {
        String email = uniqueEmail("resetreuse");
        authService.register(registerRequest("resetreuse" + suffix(email), email));
        authService.verifyEmail(emailSender.lastTokenSentTo(email));

        authService.forgotPassword(email);
        String resetToken = emailSender.lastTokenSentTo(email);
        authService.resetPassword(resetToken, "NewPassword456!");

        assertThrows(BusinessRuleException.class, () -> authService.resetPassword(resetToken, "AnotherOne789!"));
    }

    @Test
    void forgotPasswordForUnknownEmailDoesNotThrow() {
        assertDoesNotThrow(() -> authService.forgotPassword("definitely-not-registered-" + System.nanoTime() + "@test.com"));
    }

    @Test
    void resendVerificationForUnknownEmailDoesNotThrow() {
        assertDoesNotThrow(() -> authService.resendVerification("definitely-not-registered-" + System.nanoTime() + "@test.com"));
    }

    private String uniqueEmail(String label) {
        return "authit_" + label + "_" + System.nanoTime() + "@test.com";
    }

    private String suffix(String email) {
        return String.valueOf(Math.abs(email.hashCode()));
    }
}

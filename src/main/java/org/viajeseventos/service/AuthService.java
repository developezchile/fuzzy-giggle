package org.viajeseventos.service;

import org.viajeseventos.dto.request.AuthRequest;
import org.viajeseventos.dto.request.ChangePasswordRequest;
import org.viajeseventos.dto.request.RegisterRequest;
import org.viajeseventos.dto.request.UpdateMeRequest;
import org.viajeseventos.dto.response.AuthResponse;
import org.viajeseventos.dto.response.UserResponse;
import org.viajeseventos.email.EmailSender;
import org.viajeseventos.email.EmailTemplates;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.DuplicateResourceException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.exception.UnauthorizedException;
import org.viajeseventos.log.LogManager;
import org.viajeseventos.log.Logger;
import org.viajeseventos.model.EmailVerificationToken;
import org.viajeseventos.model.PasswordResetToken;
import org.viajeseventos.model.Profile;
import org.viajeseventos.model.User;
import org.viajeseventos.repository.EmailVerificationTokenRepository;
import org.viajeseventos.repository.PasswordResetTokenRepository;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.security.JwtService;
import org.viajeseventos.security.PasswordEncoder;
import org.viajeseventos.security.TokenGenerator;

import java.time.LocalDateTime;
import java.util.Map;

/** Registration + login. No {@code AuthenticationManager} — password check happens right here via {@link PasswordEncoder}. */
public final class AuthService {

    private static final Logger log = LogManager.getLogger(AuthService.class);

    private static final long VERIFICATION_TOKEN_TTL_MINUTES = 24 * 60;
    private static final long RESET_TOKEN_TTL_MINUTES = 60;

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailSender emailSender;
    private final String frontendUrl;

    public AuthService(UserRepository userRepository, ProfileRepository profileRepository,
                        EmailVerificationTokenRepository emailVerificationTokenRepository,
                        PasswordResetTokenRepository passwordResetTokenRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, EmailSender emailSender, String frontendUrl) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailSender = emailSender;
        this.frontendUrl = frontendUrl;
    }

    public void register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username)) {
            throw new DuplicateResourceException("El nombre de usuario '" + req.username + "' ya está en uso");
        }
        if (userRepository.existsByEmail(req.email)) {
            throw new DuplicateResourceException("El correo '" + req.email + "' ya está registrado");
        }

        Profile client = profileRepository.findByCode(Profile.CLIENT)
                .orElseThrow(() -> new IllegalStateException("CLIENT profile missing — V1__init.sql seeds it"));

        User user = new User();
        user.setUsername(req.username);
        user.setEmail(req.email);
        user.setPassword(passwordEncoder.encode(req.password));
        user.setFirstName(req.firstName);
        user.setLastName(req.lastName);
        user.setPhone(req.phone);
        user.setProfileId(client.getId());
        user.setEnabled(true);
        user.setEmailVerified(false);

        User saved = userRepository.insert(user);
        issueVerificationEmail(saved);

        // No session is issued here — the account isn't verified yet, so the user must go
        // through login (and thus authenticate()'s verification check) once they confirm the email.
    }

    public AuthResponse authenticate(AuthRequest req) {
        User user = userRepository.findByEmail(req.email)
                .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas"));
        if (!passwordEncoder.matches(req.password, user.getPassword())) {
            throw new BusinessRuleException("Credenciales inválidas");
        }
        if (!user.isEnabled()) {
            throw new BusinessRuleException("La cuenta está deshabilitada");
        }
        if (!user.isEmailVerified()) {
            throw new BusinessRuleException("Debes verificar tu correo electrónico antes de iniciar sesión. Revisa tu bandeja de entrada.");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, sessionUser(user));
    }

    /** The session as the frontend sees it: account + profile + granted modules. A disabled account
     *  gets a 401 here so the UI drops its stored token instead of rendering an empty menu. */
    public Map<String, Object> me(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("La sesión ya no es válida"));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("La cuenta está deshabilitada");
        }
        return sessionUser(user);
    }

    public Map<String, Object> updateMe(long userId, UpdateMeRequest req) {
        User user = requireById(userId);
        user.setFirstName(req.firstName);
        user.setLastName(req.lastName);
        user.setPhone(req.phone);
        userRepository.update(user);
        return me(userId);
    }

    public void changePassword(long userId, ChangePasswordRequest req) {
        User user = requireById(userId);
        if (!passwordEncoder.matches(req.currentPassword, user.getPassword())) {
            throw BusinessRuleException.badRequest("La contraseña actual no es correcta");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword));
        userRepository.update(user);
    }

    private User requireById(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private Map<String, Object> sessionUser(User user) {
        return UserResponse.withModules(user, profileRepository.findModulesByEnabledUserId(user.getId()));
    }

    public void verifyEmail(String rawToken) {
        EmailVerificationToken evt = emailVerificationTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BusinessRuleException("Token de verificación inválido"));
        if (evt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("El token de verificación ha expirado. Solicita uno nuevo.");
        }
        User user = userRepository.findById(evt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        user.setEmailVerified(true);
        userRepository.update(user);
        emailVerificationTokenRepository.deleteByUserId(user.getId());
    }

    /** Always no-throw regardless of whether the email exists or is already verified — the
     *  controller returns the same response either way so this can't be used to enumerate accounts. */
    public void resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                issueVerificationEmail(user);
            }
        });
    }

    /**
     * Administrator (USERS module) confirming an account's email by hand — e.g. the client never got
     * the link. Also drops any pending verification link, which would now be pointless.
     */
    public void markEmailVerified(long userId) {
        User user = requireById(userId);
        if (user.isEmailVerified()) {
            throw new BusinessRuleException("El correo ya está verificado");
        }
        user.setEmailVerified(true);
        userRepository.update(user);
        emailVerificationTokenRepository.deleteByUserId(userId);
    }

    /** Administrator (USERS module) re-sending the verification link. Unlike the public
     *  {@link #resendVerification}, this reports what happened — the caller already knows the account exists. */
    public void resendVerificationTo(long userId) {
        User user = requireById(userId);
        if (user.isEmailVerified()) {
            throw new BusinessRuleException("El correo ya está verificado");
        }
        issueVerificationEmail(user);
    }

    /** Same no-enumeration shape as {@link #resendVerification}. */
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(this::issueResetEmail);
    }

    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken prt = passwordResetTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BusinessRuleException("Token de restablecimiento inválido"));
        if (prt.getUsedAt() != null) {
            throw new BusinessRuleException("Este enlace ya fue utilizado");
        }
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("El enlace ha expirado. Solicita uno nuevo.");
        }
        User user = userRepository.findById(prt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.update(user);
        passwordResetTokenRepository.markUsed(prt.getId());
        passwordResetTokenRepository.deleteByUserId(user.getId());
    }

    private void issueVerificationEmail(User user) {
        emailVerificationTokenRepository.deleteByUserId(user.getId());

        EmailVerificationToken evt = new EmailVerificationToken();
        evt.setUserId(user.getId());
        evt.setToken(TokenGenerator.generate());
        evt.setExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_TOKEN_TTL_MINUTES));
        emailVerificationTokenRepository.insert(evt);

        String url = frontendUrl + "/verify-email?token=" + evt.getToken();
        sendEmailSafely(user.getEmail(), "Verifica tu correo — Viajes a Eventos",
                EmailTemplates.verifyEmail(user.getUsername(), url));
    }

    private void issueResetEmail(User user) {
        passwordResetTokenRepository.deleteByUserId(user.getId());

        PasswordResetToken prt = new PasswordResetToken();
        prt.setUserId(user.getId());
        prt.setToken(TokenGenerator.generate());
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_TTL_MINUTES));
        passwordResetTokenRepository.insert(prt);

        String url = frontendUrl + "/reset-password?token=" + prt.getToken();
        sendEmailSafely(user.getEmail(), "Restablece tu contraseña — Viajes a Eventos",
                EmailTemplates.resetPassword(user.getUsername(), url));
    }

    /** A flaky SMTP provider must not turn "register" or "forgot password" into a 500 — log and move on. */
    private void sendEmailSafely(String to, String subject, String htmlBody) {
        try {
            emailSender.send(to, subject, htmlBody);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", e, to, e.getMessage());
        }
    }
}

package org.viajeseventos.service;

import org.viajeseventos.dto.request.CreateUserRequest;
import org.viajeseventos.dto.request.UpdateUserRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.DuplicateResourceException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.model.Profile;
import org.viajeseventos.model.User;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.security.PasswordEncoder;

import java.util.List;
import java.util.Objects;

/** Account administration (USERS module): list, create with a profile, reassign profile, enable/disable. */
public final class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, ProfileRepository profileRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    /** Profiles an account can be assigned — lets the USERS module work without PROFILES access. */
    public List<Profile> profileOptions() {
        return profileRepository.findAll();
    }

    public User create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username)) {
            throw new DuplicateResourceException("El nombre de usuario '" + request.username + "' ya está en uso");
        }
        if (userRepository.existsByEmail(request.email)) {
            throw new DuplicateResourceException("El correo '" + request.email + "' ya está registrado");
        }
        requireProfile(request.profileId);

        User user = new User();
        user.setUsername(request.username);
        user.setEmail(request.email);
        user.setPassword(passwordEncoder.encode(request.password));
        user.setFirstName(request.firstName);
        user.setLastName(request.lastName);
        user.setPhone(request.phone);
        user.setProfileId(request.profileId);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return findById(userRepository.insert(user).getId());
    }

    public User update(long callerId, long id, UpdateUserRequest request) {
        User user = findById(id);
        Profile target = requireProfile(request.profileId);
        boolean profileChanges = !Objects.equals(user.getProfileId(), request.profileId);

        if (id == callerId) {
            if (!request.enabled) {
                throw new BusinessRuleException("No puedes deshabilitar tu propia cuenta");
            }
            if (profileChanges) {
                throw new BusinessRuleException("No puedes cambiar tu propio perfil");
            }
        }

        boolean wasActiveAdmin = Profile.ADMIN.equals(user.getProfileCode()) && user.isEnabled();
        boolean staysActiveAdmin = target.isAdmin() && request.enabled;
        if (wasActiveAdmin && !staysActiveAdmin
                && userRepository.countEnabledByProfileId(user.getProfileId()) <= 1) {
            throw new BusinessRuleException("Debe quedar al menos un administrador habilitado");
        }

        user.setFirstName(request.firstName);
        user.setLastName(request.lastName);
        user.setPhone(request.phone);
        user.setProfileId(request.profileId);
        user.setEnabled(request.enabled);
        userRepository.update(user);
        return findById(id);
    }

    private Profile requireProfile(long profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> BusinessRuleException.badRequest("El perfil seleccionado no existe"));
    }
}

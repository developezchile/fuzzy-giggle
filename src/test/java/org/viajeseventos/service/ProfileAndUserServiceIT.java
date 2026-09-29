package org.viajeseventos.service;

import org.viajeseventos.db.ConnectionPool;
import org.viajeseventos.dto.request.CreateUserRequest;
import org.viajeseventos.dto.request.ProfileRequest;
import org.viajeseventos.dto.request.UpdateUserRequest;
import org.viajeseventos.exception.BusinessRuleException;
import org.viajeseventos.exception.DuplicateResourceException;
import org.viajeseventos.exception.ResourceNotFoundException;
import org.viajeseventos.exception.ValidationException;
import org.viajeseventos.model.AppModule;
import org.viajeseventos.model.Profile;
import org.viajeseventos.model.User;
import org.viajeseventos.repository.ProfileRepository;
import org.viajeseventos.repository.UserRepository;
import org.viajeseventos.security.Caller;
import org.viajeseventos.security.PasswordEncoder;
import org.viajeseventos.testsupport.TestDb;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The profile maintainer and account administration against a real local Postgres (see {@link TestDb}):
 * that a profile's modules are what {@code ModuleAccess} reads, and that the lock-out guards hold.
 */
class ProfileAndUserServiceIT {

    private static ConnectionPool pool;
    private static ProfileRepository profileRepository;
    private static UserRepository userRepository;
    private static ProfileService profileService;
    private static UserService userService;
    private static long companyId;

    @BeforeAll
    static void setUp() {
        pool = TestDb.pool();
        profileRepository = new ProfileRepository(pool);
        userRepository = new UserRepository(pool);
        profileService = new ProfileService(profileRepository);
        userService = new UserService(userRepository, profileRepository, new PasswordEncoder());
        companyId = TestDb.seededCompanyId(pool);
    }

    @AfterAll
    static void tearDown() {
        pool.close();
    }

    @Test
    void modulesGrantedToAProfileAreWhatItsUsersGet() {
        Profile coordinator = profileService.create(profileRequest(unique("Coordinador"), "EVENTS", "USERS"));
        User user = createUser(coordinator.getId());

        assertEquals(Set.of(AppModule.EVENTS, AppModule.USERS),
                profileRepository.findModulesByEnabledUserId(user.getId()));

        profileService.update(coordinator.getId(), profileRequest(coordinator.getName(), "EVENTS"));
        assertEquals(Set.of(AppModule.EVENTS), profileRepository.findModulesByEnabledUserId(user.getId()));
    }

    @Test
    void disabledUserHasNoModules() {
        Profile profile = profileService.create(profileRequest(unique("Deshabilitable"), "EVENTS"));
        User user = createUser(profile.getId());

        userService.update(someone(), user.getId(), updateRequest(profile.getId(), false));

        assertTrue(profileRepository.findModulesByEnabledUserId(user.getId()).isEmpty());
    }

    @Test
    void platformModulesCannotBeGrantedThroughAProfile() {
        Profile sneaky = profileService.create(profileRequest(unique("Colado"), "EVENTS", "PROFILES", "SETTINGS", "COMPANIES"));
        assertEquals(Set.of(AppModule.EVENTS), sneaky.getModules());
        assertEquals(Set.of(AppModule.EVENTS), profileRepository.findModulesByEnabledUserId(createUser(sneaky.getId()).getId()));
    }

    @Test
    void anotherCompanysUsersAreNotFound() {
        User mine = createUser(clientProfileId());
        long otherCompany = TestDb.newCompanyId(pool);

        assertThrows(ResourceNotFoundException.class, () -> userService.findById(otherCompany, mine.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> userService.update(new Caller(-1, otherCompany), mine.getId(), updateRequest(clientProfileId(), false)));
        assertTrue(userService.findAll(otherCompany).isEmpty());
    }

    @Test
    void profileNamesAreUniqueIgnoringCase() {
        String name = unique("Vendedor");
        profileService.create(profileRequest(name, "EVENTS"));
        assertThrows(DuplicateResourceException.class,
                () -> profileService.create(profileRequest(name.toUpperCase(), "EVENTS")));
    }

    @Test
    void unknownModuleKeyIsRejected() {
        assertThrows(ValidationException.class, () -> profileRequest(unique("X"), "EVENTS", "NO_EXISTE"));
    }

    @Test
    void adminProfileKeepsEveryModule() {
        Profile admin = profileRepository.findByCode(Profile.ADMIN).orElseThrow();
        assertThrows(BusinessRuleException.class,
                () -> profileService.update(admin.getId(), profileRequest(admin.getName(), "EVENTS")));
    }

    @Test
    void systemProfilesCannotBeDeleted() {
        Profile client = profileRepository.findByCode(Profile.CLIENT).orElseThrow();
        assertThrows(BusinessRuleException.class, () -> profileService.delete(client.getId()));
    }

    @Test
    void profileWithUsersCannotBeDeletedUntilTheyAreReassigned() {
        Profile profile = profileService.create(profileRequest(unique("Temporal"), "EVENTS"));
        User user = createUser(profile.getId());
        assertThrows(BusinessRuleException.class, () -> profileService.delete(profile.getId()));

        userService.update(someone(), user.getId(), updateRequest(clientProfileId(), true));
        profileService.delete(profile.getId());
        assertTrue(profileRepository.findById(profile.getId()).isEmpty());
    }

    @Test
    void usersCannotDisableThemselvesOrChangeTheirOwnProfile() {
        Profile other = profileService.create(profileRequest(unique("Otro"), "EVENTS"));
        User user = createUser(clientProfileId());

        assertThrows(BusinessRuleException.class,
                () -> userService.update(new Caller(user.getId(), companyId), user.getId(), updateRequest(clientProfileId(), false)));
        assertThrows(BusinessRuleException.class,
                () -> userService.update(new Caller(user.getId(), companyId), user.getId(), updateRequest(other.getId(), true)));
    }

    @Test
    void aCompanysLastEnabledAdminCannotBeDemotedOrDisabled() {
        long adminProfileId = profileRepository.findByCode(Profile.ADMIN).orElseThrow().getId();
        // A fresh company: its only admin, whatever other companies have.
        long company = TestDb.newCompanyId(pool);
        Caller someone = new Caller(-1, company);
        User lastAdmin = createUser(company, adminProfileId);

        assertThrows(BusinessRuleException.class,
                () -> userService.update(someone, lastAdmin.getId(), updateRequest(adminProfileId, false)));
        assertThrows(BusinessRuleException.class,
                () -> userService.update(someone, lastAdmin.getId(), updateRequest(clientProfileId(), true)));

        // A second admin makes demoting the first one fine.
        createUser(company, adminProfileId);
        userService.update(someone, lastAdmin.getId(), updateRequest(clientProfileId(), true));
    }

    // ---- helpers ----

    private static String unique(String name) {
        return name + " " + System.nanoTime();
    }

    private static ProfileRequest profileRequest(String name, String... modules) {
        return ProfileRequest.fromJson(Map.of("name", name, "modules", List.of(modules)));
    }

    private static long clientProfileId() {
        return profileRepository.findByCode(Profile.CLIENT).orElseThrow().getId();
    }

    /** An administrator of the seeded company other than the accounts under test. */
    private static Caller someone() {
        return new Caller(-1, companyId);
    }

    private static User createUser(long profileId) {
        return createUser(companyId, profileId);
    }

    private static User createUser(long company, long profileId) {
        String unique = String.valueOf(System.nanoTime());
        return userService.create(company, CreateUserRequest.fromJson(Map.of(
                "username", "it" + unique, "email", "it_" + unique + "@test.com",
                "password", "Password123!", "profileId", (double) profileId)));
    }

    private static UpdateUserRequest updateRequest(long profileId, boolean enabled) {
        return UpdateUserRequest.fromJson(Map.of("profileId", (double) profileId, "enabled", enabled));
    }
}

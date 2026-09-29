package org.viajeseventos.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = new PasswordEncoder();

    @Test
    void encodedPasswordMatchesOriginal() {
        String hash = encoder.encode("correct horse battery staple");
        assertTrue(encoder.matches("correct horse battery staple", hash));
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hash = encoder.encode("correct horse battery staple");
        assertFalse(encoder.matches("wrong password", hash));
    }

    @Test
    void blankOrNullHashNeverMatches() {
        assertFalse(encoder.matches("anything", null));
        assertFalse(encoder.matches("anything", ""));
    }

    @Test
    void isCompatibleWithTheSeededAdminHash() {
        // The seeded admin user's hash from db/migrations/V1__init.sql (admin123) must verify here.
        String hash = "$2y$10$XDpVuMGau41xtA2nUi38veuA0n26gNh.FPP9c.qeqqAvycrWbfdMW";
        assertTrue(encoder.matches("admin123", hash));
    }
}

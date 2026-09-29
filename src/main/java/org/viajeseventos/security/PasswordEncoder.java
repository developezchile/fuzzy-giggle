package org.viajeseventos.security;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Thin wrapper around the {@code at.favre.lib:bcrypt} library (small, dependency-free, pure
 * Java — no hand-rolled crypto here since getting a hash algorithm subtly wrong is a real
 * security risk).
 */
public final class PasswordEncoder {

    private static final int COST_FACTOR = 10;

    public String encode(String rawPassword) {
        return BCrypt.withDefaults().hashToString(COST_FACTOR, rawPassword.toCharArray());
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) return false;
        return BCrypt.verifyer().verify(rawPassword.toCharArray(), encodedPassword).verified;
    }
}

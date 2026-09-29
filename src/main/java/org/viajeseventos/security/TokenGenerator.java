package org.viajeseventos.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Opaque, high-entropy tokens for email verification / password reset links — not JWTs, since
 *  these need to be revocable (deleted from their table) the moment they're used or superseded. */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

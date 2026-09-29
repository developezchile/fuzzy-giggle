package org.viajeseventos.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "Y2FsZW5kYXJpb1NlY3JldEtleUZvckpXVEF1dGgyNTZCaXRzTG9uZ0Vub3VnaA==";

    @Test
    void roundTripsUserIdAndEmail() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        String token = jwt.generateToken(42, "client@test.com");

        assertTrue(jwt.validate(token));
        assertEquals(42, jwt.userIdFromToken(token));
        assertEquals("client@test.com", jwt.emailFromToken(token));
    }

    @Test
    void rejectsTamperedSignature() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        String token = jwt.generateToken(1, "a@test.com");
        String[] parts = token.split("\\.");
        // Flip the *first* character of the signature — unlike the last character of an unpadded
        // base64url string, it always encodes real signature bits, so this deterministically
        // changes the decoded bytes (a last-character flip can land on bits base64 ignores).
        char first = parts[2].charAt(0);
        char replacement = first == 'a' ? 'b' : 'a';
        String tampered = parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);

        assertFalse(jwt.validate(tampered));
    }

    @Test
    void rejectsTamperedPayload() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        String token = jwt.generateToken(1, "a@test.com");
        String[] parts = token.split("\\.");
        String otherPayload = new JwtService(SECRET, 60_000).generateToken(999, "attacker@test.com").split("\\.")[1];
        String forged = parts[0] + "." + otherPayload + "." + parts[2];

        assertFalse(jwt.validate(forged));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = new JwtService(SECRET, -1_000); // already expired the moment it's issued
        String token = jwt.generateToken(1, "a@test.com");

        assertFalse(jwt.validate(token));
    }

    @Test
    void rejectsMalformedToken() {
        JwtService jwt = new JwtService(SECRET, 60_000);

        assertFalse(jwt.validate("not-a-jwt"));
        assertFalse(jwt.validate("a.b"));
        assertFalse(jwt.validate(""));
    }

    @Test
    void differentSecretsProduceIncompatibleTokens() {
        JwtService issuer = new JwtService(SECRET, 60_000);
        JwtService verifier = new JwtService("differentSecretValueEntirely", 60_000);
        String token = issuer.generateToken(1, "a@test.com");

        assertFalse(verifier.validate(token));
    }
}

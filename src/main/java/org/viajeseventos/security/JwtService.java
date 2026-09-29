package org.viajeseventos.security;

import org.viajeseventos.json.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

/**
 * Minimal hand-rolled HS256 JWT signer/verifier — just HMAC-SHA256 over
 * {@code base64url(header).base64url(payload)}, which is all a JWT is. Replaces JJWT.
 *
 * <p>The token only identifies the user; it carries no roles or modules. Access is decided per
 * request from the user's current profile (see {@link ModuleAccess}), so changing a profile or
 * disabling an account applies immediately instead of when outstanding tokens expire.
 */
public final class JwtService {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secretKeyBytes;
    private final long expirationMs;

    public JwtService(String secret, long expirationMs) {
        this.secretKeyBytes = decodeSecret(secret);
        this.expirationMs = expirationMs;
    }

    private static byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            // Fall back to raw UTF-8 bytes if the configured secret isn't valid base64.
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String generateToken(long userId, String email) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = Json.obj();
        claims.put("sub", email);
        claims.put("userId", (double) userId);
        claims.put("iat", now / 1000.0);
        claims.put("exp", (now + expirationMs) / 1000.0);
        return sign(claims);
    }

    private String sign(Map<String, Object> claims) {
        String headerB64 = B64.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = B64.encodeToString(Json.write(claims).getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;
        String signature = B64.encodeToString(hmacSha256(signingInput));
        return signingInput + "." + signature;
    }

    public boolean validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSig = hmacSha256(signingInput);
            byte[] actualSig = B64D.decode(parts[2]);
            if (!constantTimeEquals(expectedSig, actualSig)) return false;

            Map<String, Object> claims = parseClaims(parts[1]);
            Object exp = claims.get("exp");
            if (exp instanceof Number n) {
                return System.currentTimeMillis() / 1000.0 < n.doubleValue();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public String emailFromToken(String token) {
        return (String) parseClaims(payloadSegment(token)).get("sub");
    }

    public long userIdFromToken(String token) {
        Object userId = parseClaims(payloadSegment(token)).get("userId");
        if (userId == null) {
            throw new IllegalStateException("Token has no userId claim");
        }
        return ((Number) userId).longValue();
    }

    private String payloadSegment(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed JWT");
        }
        return parts[1];
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseClaims(String payloadB64) {
        String json = new String(B64D.decode(payloadB64), StandardCharsets.UTF_8);
        return (Map<String, Object>) Json.parse(json);
    }

    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKeyBytes, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute JWT signature", e);
        }
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}

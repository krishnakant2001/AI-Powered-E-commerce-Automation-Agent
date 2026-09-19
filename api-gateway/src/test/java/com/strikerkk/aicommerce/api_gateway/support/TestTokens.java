package com.strikerkk.aicommerce.api_gateway.support;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * Shared, always-valid JWT fixtures for the api-gateway test suite.
 *
 * <p>The gateway only ever <em>reads</em> tokens (they are minted by the user-service), so the
 * tests have to be able to mint them as well. Every helper here signs with {@link #SECRET},
 * which is also the value injected into the Spring context by {@link #JWT_SECRET_PROPERTY}.
 */
public final class TestTokens {

    /** HS256 needs at least 32 bytes of key material, both constants are comfortably longer. */
    public static final String SECRET =
            "api-gateway-test-secret-key-0123456789-abcdefghijklmnopqrstuvwxyz";

    public static final String OTHER_SECRET =
            "another-totally-different-gateway-key-9876543210-zyxwvutsrqponmlkj";

    /**
     * Compile-time constant, so it can be used inside {@code @SpringBootTest(properties = ...)}.
     * It shadows {@code jwt.secretKey: ${JWT_SECRET_KEY}} from {@code src/main/resources/application.yml},
     * which means the tests never need a {@code JWT_SECRET_KEY} environment variable.
     */
    public static final String JWT_SECRET_PROPERTY = "jwt.secretKey=" + SECRET;

    /** Keeps the whole suite off the network: no Eureka registration, no registry fetch. */
    public static final String EUREKA_DISABLED_PROPERTY = "eureka.client.enabled=false";
    public static final String EUREKA_NO_REGISTER_PROPERTY = "eureka.client.register-with-eureka=false";
    public static final String EUREKA_NO_FETCH_PROPERTY = "eureka.client.fetch-registry=false";

    public static final String USER_ID = "42";
    public static final String ROLE = "USER";
    public static final String EMAIL = "striker@example.com";

    private TestTokens() {
        throw new AssertionError("utility class");
    }

    public static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** The happy-path token: subject {@value #USER_ID}, role {@value #ROLE}, email {@value #EMAIL}. */
    public static String validToken() {
        return token(USER_ID, ROLE, EMAIL, SECRET);
    }

    public static String token(String userId, String role, String email, String secret) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)));

        if (userId != null) {
            builder.subject(userId);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        if (email != null) {
            builder.claim("email", email);
        }
        return builder.signWith(key(secret)).compact();
    }

    /** Correctly signed, but carries neither the {@code role} nor the {@code email} claim. */
    public static String tokenWithoutCustomClaims() {
        return token(USER_ID, null, null, SECRET);
    }

    /** Correctly signed with {@link #SECRET}, but already expired. */
    public static String expiredToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(USER_ID)
                .claim("role", ROLE)
                .claim("email", EMAIL)
                .issuedAt(Date.from(now.minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(now.minus(1, ChronoUnit.HOURS)))
                .signWith(key(SECRET))
                .compact();
    }

    /** Well-formed, but signed with a key the gateway does not know. */
    public static String tokenSignedWithAnotherKey() {
        return token(USER_ID, "ADMIN", EMAIL, OTHER_SECRET);
    }

    /**
     * Valid header + valid JSON payload, but the signature belongs to a different payload — the
     * classic "privilege escalation by editing the payload" attempt.
     */
    public static String tokenWithTamperedPayload() {
        String[] parts = validToken().split("\\.");
        String forgedPayload = base64Url("{\"sub\":\"999\",\"role\":\"ADMIN\",\"email\":\"attacker@example.com\"}");
        return parts[0] + "." + forgedPayload + "." + parts[2];
    }

    /** A JWT with {@code "alg":"none"} and an empty signature. */
    public static String unsecuredToken() {
        return base64Url("{\"alg\":\"none\"}") + "." + base64Url("{\"sub\":\"" + USER_ID + "\"}") + ".";
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}




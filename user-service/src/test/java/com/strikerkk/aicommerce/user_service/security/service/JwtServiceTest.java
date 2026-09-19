package com.strikerkk.aicommerce.user_service.security.service;

import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.entity.enums.Role;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET_KEY =
            "user-service-test-secret-key-0123456789-abcdefghijklmnopqrstuvwxyz";
    private static final String OTHER_SECRET_KEY =
            "another-totally-different-secret-key-0123456789-abcdefghijklmnop";

    private static final long THREE_DAYS_IN_SECONDS = 3L * 24 * 60 * 60;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecretKey", SECRET_KEY);
    }

    private static Claims parse(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    @DisplayName("generates a signed token that carries the user id as the subject")
    void shouldPutUserIdInSubject() {
        User user = TestDataFactory.user();

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(parse(token, SECRET_KEY).getSubject()).isEqualTo(String.valueOf(user.getId()));
    }

    @Test
    @DisplayName("adds the email and the role as custom claims")
    void shouldAddEmailAndRoleClaims() {
        User user = TestDataFactory.user();
        user.setRole(Role.ADMIN);

        Claims claims = parse(jwtService.generateAccessToken(user), SECRET_KEY);

        assertThat(claims.get("email", String.class)).isEqualTo(TestDataFactory.EMAIL);
        assertThat(claims.get("role", String.class)).isEqualTo(Role.ADMIN.name());
    }

    @Test
    @DisplayName("keeps the USER role for a standard account")
    void shouldKeepUserRoleForStandardAccount() {
        Claims claims = parse(jwtService.generateAccessToken(TestDataFactory.user()), SECRET_KEY);

        assertThat(claims.get("role", String.class)).isEqualTo(Role.USER.name());
    }

    @Test
    @DisplayName("issues a token that expires three days after it was issued")
    void shouldExpireInThreeDays() {
        Claims claims = parse(jwtService.generateAccessToken(TestDataFactory.user()), SECRET_KEY);

        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();

        assertThat(issuedAt).isNotNull();
        assertThat(expiration).isNotNull().isAfter(issuedAt);

        long lifetimeInSeconds = (expiration.getTime() - issuedAt.getTime()) / 1000L;
        assertThat(lifetimeInSeconds)
                .isBetween(THREE_DAYS_IN_SECONDS - 5, THREE_DAYS_IN_SECONDS + 5);
    }

    @Test
    @DisplayName("never issues the same token for two different users")
    void shouldGenerateDifferentTokensForDifferentUsers() {
        User first = TestDataFactory.user();

        User second = TestDataFactory.user();
        second.setId(2L);
        second.setEmail("second@example.com");

        assertThat(jwtService.generateAccessToken(first))
                .isNotEqualTo(jwtService.generateAccessToken(second));
    }

    @Test
    @DisplayName("rejects a token that was signed with a different secret")
    void shouldRejectTokenSignedWithAnotherKey() {
        String token = jwtService.generateAccessToken(TestDataFactory.user());

        assertThatThrownBy(() -> parse(token, OTHER_SECRET_KEY))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rejects a tampered token")
    void shouldRejectTamperedToken() {
        String token = jwtService.generateAccessToken(TestDataFactory.user());
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> parse(tampered, SECRET_KEY))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("fails fast when the user has no id yet")
    void shouldFailWhenUserHasNoId() {
        User user = TestDataFactory.user();
        user.setId(null);

        assertThatThrownBy(() -> jwtService.generateAccessToken(user))
                .isInstanceOf(NullPointerException.class);
    }
}


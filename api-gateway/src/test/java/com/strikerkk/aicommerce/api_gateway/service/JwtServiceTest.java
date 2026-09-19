package com.strikerkk.aicommerce.api_gateway.service;

import com.strikerkk.aicommerce.api_gateway.dto.TokenClaims;
import com.strikerkk.aicommerce.api_gateway.support.TestTokens;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecretKey", TestTokens.SECRET);
    }

    @Nested
    @DisplayName("when the token is valid")
    class ValidToken {

        @Test
        @DisplayName("maps the subject, the role and the email onto TokenClaims")
        void shouldExtractAllClaims() {
            TokenClaims claims = jwtService.getClaimsFromToken(TestTokens.validToken());

            assertThat(claims).isNotNull();
            assertThat(claims.getUserId()).isEqualTo(TestTokens.USER_ID);
            assertThat(claims.getRole()).isEqualTo(TestTokens.ROLE);
            assertThat(claims.getEmail()).isEqualTo(TestTokens.EMAIL);
        }

        @Test
        @DisplayName("keeps the subject as a String, it is never parsed into a number")
        void shouldKeepSubjectAsString() {
            String token = TestTokens.token("100200300", "USER", "a@b.com", TestTokens.SECRET);

            assertThat(jwtService.getClaimsFromToken(token).getUserId()).isEqualTo("100200300");
        }

        @Test
        @DisplayName("propagates the ADMIN role verbatim, without a ROLE_ prefix")
        void shouldPropagateAdminRoleVerbatim() {
            String token = TestTokens.token(TestTokens.USER_ID, "ADMIN", TestTokens.EMAIL, TestTokens.SECRET);

            assertThat(jwtService.getClaimsFromToken(token).getRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("returns null claims instead of failing when role/email are missing")
        void shouldTolerateMissingCustomClaims() {
            TokenClaims claims = jwtService.getClaimsFromToken(TestTokens.tokenWithoutCustomClaims());

            assertThat(claims.getUserId()).isEqualTo(TestTokens.USER_ID);
            assertThat(claims.getRole()).isNull();
            assertThat(claims.getEmail()).isNull();
        }

        @Test
        @DisplayName("returns a null userId when the token carries no subject")
        void shouldTolerateMissingSubject() {
            String token = TestTokens.token(null, TestTokens.ROLE, TestTokens.EMAIL, TestTokens.SECRET);

            TokenClaims claims = jwtService.getClaimsFromToken(token);

            assertThat(claims.getUserId()).isNull();
            assertThat(claims.getRole()).isEqualTo(TestTokens.ROLE);
        }

        @Test
        @DisplayName("is side-effect free: parsing the same token twice yields equal claims")
        void shouldBeIdempotent() {
            String token = TestTokens.validToken();

            assertThat(jwtService.getClaimsFromToken(token))
                    .isEqualTo(jwtService.getClaimsFromToken(token));
        }
    }

    @Nested
    @DisplayName("when the token must be rejected")
    class InvalidToken {

        @Test
        @DisplayName("rejects a token signed with a different secret")
        void shouldRejectTokenSignedWithAnotherKey() {
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(TestTokens.tokenSignedWithAnotherKey()))
                    .isInstanceOf(SignatureException.class);
        }

        @Test
        @DisplayName("rejects a token whose payload was edited after signing")
        void shouldRejectTamperedPayload() {
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(TestTokens.tokenWithTamperedPayload()))
                    .isInstanceOf(SignatureException.class);
        }

        @Test
        @DisplayName("rejects an expired token")
        void shouldRejectExpiredToken() {
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(TestTokens.expiredToken()))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("rejects an unsecured ('alg':'none') token")
        void shouldRejectUnsecuredToken() {
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(TestTokens.unsecuredToken()))
                    .isInstanceOf(JwtException.class);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"not-a-jwt", "abc.def", "a.b.c.d", "Bearer eyJhbGciOiJIUzI1NiJ9"})
        @DisplayName("rejects a malformed compact token")
        void shouldRejectMalformedToken(String malformed) {
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(malformed))
                    .isInstanceOf(MalformedJwtException.class);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @ValueSource(strings = {"", "   "})
        @DisplayName("rejects a blank token with IllegalArgumentException")
        void shouldRejectBlankToken(String blank) {
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(blank))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects a null token with IllegalArgumentException")
        void shouldRejectNullToken() {
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("every failure is a RuntimeException, which is what AuthenticationFilter catches")
        void everyFailureIsARuntimeException() {
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(TestTokens.expiredToken()))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(TestTokens.tokenSignedWithAnotherKey()))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> jwtService.getClaimsFromToken("garbage"))
                    .isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> jwtService.getClaimsFromToken(null))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("secret key handling")
    class SecretKeyHandling {

        @Test
        @DisplayName("derives the HMAC key from the UTF-8 bytes of jwt.secretKey")
        void shouldDeriveKeyFromConfiguredSecret() {
            // the token below is signed with exactly `Keys.hmacShaKeyFor(SECRET.getBytes(UTF_8))`
            assertThat(jwtService.getClaimsFromToken(TestTokens.validToken())).isNotNull();
        }

        @Test
        @DisplayName("a gateway configured with another secret rejects the user-service tokens")
        void shouldRejectWhenConfiguredWithAnotherSecret() {
            JwtService otherGateway = new JwtService();
            ReflectionTestUtils.setField(otherGateway, "jwtSecretKey", TestTokens.OTHER_SECRET);

            String token = TestTokens.validToken();

            assertThat(jwtService.getClaimsFromToken(token)).isNotNull();
            assertThatThrownBy(() -> otherGateway.getClaimsFromToken(token))
                    .isInstanceOf(SignatureException.class);
        }

        @Test
        @DisplayName("fails fast when jwt.secretKey was never configured")
        void shouldFailWhenSecretIsMissing() {
            JwtService misconfigured = new JwtService();

            assertThatThrownBy(() -> misconfigured.getClaimsFromToken(TestTokens.validToken()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("fails fast when jwt.secretKey is too short for HS256 (< 256 bits)")
        void shouldFailWhenSecretIsTooShort() {
            JwtService weak = new JwtService();
            ReflectionTestUtils.setField(weak, "jwtSecretKey", "too-short");

            assertThatThrownBy(() -> weak.getClaimsFromToken(TestTokens.validToken()))
                    .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
        }
    }
}


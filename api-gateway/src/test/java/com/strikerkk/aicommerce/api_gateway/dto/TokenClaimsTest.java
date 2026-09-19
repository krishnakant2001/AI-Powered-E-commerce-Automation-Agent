package com.strikerkk.aicommerce.api_gateway.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenClaims")
class TokenClaimsTest {

    private static final String USER_ID = "42";
    private static final String ROLE = "ADMIN";
    private static final String EMAIL = "striker@example.com";

    @Test
    @DisplayName("exposes the three values passed to the constructor")
    void shouldExposeConstructorValues() {
        TokenClaims claims = new TokenClaims(USER_ID, ROLE, EMAIL);

        assertThat(claims.getUserId()).isEqualTo(USER_ID);
        assertThat(claims.getRole()).isEqualTo(ROLE);
        assertThat(claims.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("keeps the constructor argument order userId, role, email")
    void shouldKeepConstructorArgumentOrder() {
        TokenClaims claims = new TokenClaims("first", "second", "third");

        assertThat(claims.getUserId()).isEqualTo("first");
        assertThat(claims.getRole()).isEqualTo("second");
        assertThat(claims.getEmail()).isEqualTo("third");
    }

    @Test
    @DisplayName("accepts null values, a JWT may omit the role/email claims")
    void shouldAcceptNullValues() {
        TokenClaims claims = new TokenClaims(USER_ID, null, null);

        assertThat(claims.getUserId()).isEqualTo(USER_ID);
        assertThat(claims.getRole()).isNull();
        assertThat(claims.getEmail()).isNull();
    }

    @Test
    @DisplayName("two instances carrying the same values are equal and share the hash code")
    void shouldImplementValueEquality() {
        TokenClaims first = new TokenClaims(USER_ID, ROLE, EMAIL);
        TokenClaims second = new TokenClaims(USER_ID, ROLE, EMAIL);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isEqualTo(first);
        assertThat(first).isNotEqualTo(null);
        assertThat(first).isNotEqualTo("some string");
    }

    @Test
    @DisplayName("a difference in any single field breaks equality")
    void shouldNotBeEqualWhenAnyFieldDiffers() {
        TokenClaims reference = new TokenClaims(USER_ID, ROLE, EMAIL);

        assertThat(reference).isNotEqualTo(new TokenClaims("43", ROLE, EMAIL));
        assertThat(reference).isNotEqualTo(new TokenClaims(USER_ID, "USER", EMAIL));
        assertThat(reference).isNotEqualTo(new TokenClaims(USER_ID, ROLE, "other@example.com"));
    }

    @Test
    @DisplayName("toString renders every field, which is what shows up in the gateway logs")
    void shouldRenderEveryFieldInToString() {
        assertThat(new TokenClaims(USER_ID, ROLE, EMAIL).toString())
                .contains("TokenClaims")
                .contains("userId=" + USER_ID)
                .contains("role=" + ROLE)
                .contains("email=" + EMAIL);
    }

    @Test
    @DisplayName("is immutable: every field is final and no setter is generated")
    void shouldBeImmutable() {
        for (Field field : TokenClaims.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            assertThat(Modifier.isFinal(field.getModifiers()))
                    .as("field '%s' must be final", field.getName())
                    .isTrue();
        }

        assertThat(TokenClaims.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set"));
    }

    @Test
    @DisplayName("declares exactly one public three-arg constructor")
    void shouldDeclareSingleAllArgsConstructor() {
        Constructor<?>[] constructors = TokenClaims.class.getConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(String.class, String.class, String.class);
    }
}


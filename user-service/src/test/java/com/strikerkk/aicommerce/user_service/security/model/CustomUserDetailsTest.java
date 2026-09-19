package com.strikerkk.aicommerce.user_service.security.model;

import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomUserDetails")
class CustomUserDetailsTest {

    private User user;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        user = TestDataFactory.user();
        userDetails = new CustomUserDetails(user);
    }

    @Test
    @DisplayName("uses the email as the Spring Security username")
    void shouldUseEmailAsUsername() {
        assertThat(userDetails.getUsername()).isEqualTo(TestDataFactory.EMAIL);
    }

    @Test
    @DisplayName("exposes the encoded password")
    void shouldExposeEncodedPassword() {
        assertThat(userDetails.getPassword()).isEqualTo(TestDataFactory.ENCODED_PASSWORD);
    }

    @Test
    @DisplayName("exposes the wrapped user so that the JWT can be built from it")
    void shouldExposeWrappedUser() {
        assertThat(userDetails.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("grants no authority - authorization is driven by the gateway headers instead")
    void shouldGrantNoAuthority() {
        assertThat(userDetails.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("reports the account as fully usable")
    void shouldReportAccountAsUsable() {
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("reflects a change of the wrapped user")
    void shouldReflectWrappedUserChanges() {
        user.setEmail("changed@example.com");
        user.setPassword("new-encoded");

        assertThat(userDetails.getUsername()).isEqualTo("changed@example.com");
        assertThat(userDetails.getPassword()).isEqualTo("new-encoded");
    }
}


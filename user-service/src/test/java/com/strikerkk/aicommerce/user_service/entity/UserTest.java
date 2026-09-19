package com.strikerkk.aicommerce.user_service.entity;

import com.strikerkk.aicommerce.user_service.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User entity")
class UserTest {

    private static User newUser() {
        return User.builder()
                .firstName("Krishnakant")
                .lastName("Nagvanshi")
                .email("krishna@example.com")
                .password("secret")
                .build();
    }

    @Test
    @DisplayName("defaults the role to USER and the address list to an empty list")
    void shouldApplyBuilderDefaults() {
        User user = newUser();

        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getAddresses()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("allows the role to be overridden")
    void shouldAllowRoleToBeOverridden() {
        User user = User.builder()
                .firstName("Krishnakant")
                .email("admin@example.com")
                .password("secret")
                .role(Role.ADMIN)
                .build();

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("@PrePersist stamps both createdAt and updatedAt")
    void shouldStampTimestampsOnCreate() {
        User user = newUser();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        user.onCreate();

        assertThat(user.getCreatedAt()).isNotNull().isAfter(before);
        assertThat(user.getUpdatedAt()).isNotNull().isAfter(before);
    }

    @Test
    @DisplayName("@PreUpdate refreshes updatedAt but keeps createdAt untouched")
    void shouldOnlyRefreshUpdatedAtOnUpdate() throws InterruptedException {
        User user = newUser();
        user.onCreate();
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime firstUpdatedAt = user.getUpdatedAt();

        Thread.sleep(5);
        user.onUpdate();

        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(firstUpdatedAt);
    }

    @Test
    @DisplayName("exposes every mutable attribute through its setters")
    void shouldExposeSetters() {
        User user = newUser();
        List<Address> addresses = new ArrayList<>();

        user.setId(5L);
        user.setGoogleId("google-sub");
        user.setFirstName("Krishnakant");
        user.setLastName("Nagvanshi");
        user.setEmail("krishna@example.com");
        user.setPassword("encoded");
        user.setPhoneNumber("9876543210");
        user.setRole(Role.ADMIN);
        user.setAddresses(addresses);

        assertThat(user.getId()).isEqualTo(5L);
        assertThat(user.getGoogleId()).isEqualTo("google-sub");
        assertThat(user.getFirstName()).isEqualTo("Krishnakant");
        assertThat(user.getLastName()).isEqualTo("Nagvanshi");
        assertThat(user.getEmail()).isEqualTo("krishna@example.com");
        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.getPhoneNumber()).isEqualTo("9876543210");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getAddresses()).isSameAs(addresses);
    }

    @Test
    @DisplayName("Role exposes exactly the ADMIN and USER values")
    void shouldExposeOnlyTwoRoles() {
        assertThat(Role.values()).containsExactly(Role.ADMIN, Role.USER);
        assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
        assertThat(Role.valueOf("USER")).isEqualTo(Role.USER);
    }
}




package com.strikerkk.aicommerce.user_service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserContext")
class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("stores and returns the user id and the user role")
    void shouldStoreAndReturnValues() {
        UserContext.setUserId("42");
        UserContext.setUserRole("ADMIN");

        assertThat(UserContext.getUserId()).isEqualTo("42");
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("returns null when nothing has been stored")
    void shouldReturnNullWhenEmpty() {
        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("clear() removes both values")
    void shouldClearValues() {
        UserContext.setUserId("42");
        UserContext.setUserRole("USER");

        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("keeps the values isolated per thread")
    void shouldBeThreadLocal() throws InterruptedException {
        UserContext.setUserId("main-thread-user");
        UserContext.setUserRole("ADMIN");

        AtomicReference<String> otherThreadUserId = new AtomicReference<>("not-executed");
        AtomicReference<String> otherThreadUserRole = new AtomicReference<>("not-executed");

        Thread other = new Thread(() -> {
            otherThreadUserId.set(UserContext.getUserId());
            otherThreadUserRole.set(UserContext.getUserRole());
        });
        other.start();
        other.join();

        assertThat(otherThreadUserId.get()).isNull();
        assertThat(otherThreadUserRole.get()).isNull();
        assertThat(UserContext.getUserId()).isEqualTo("main-thread-user");
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }
}


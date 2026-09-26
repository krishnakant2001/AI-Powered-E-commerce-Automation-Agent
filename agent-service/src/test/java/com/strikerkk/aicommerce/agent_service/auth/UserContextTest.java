package com.strikerkk.aicommerce.agent_service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserContext")
class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
        UserContext.setUserRole(null);
        UserContext.setUserEmail(null);
    }

    @Test
    @DisplayName("returns null before anything has been stored")
    void returnsNullWhenEmpty() {
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("stores and returns the caller id")
    void storesTheCallerId() {
        UserContext.setUserId("42");

        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("stores and returns the caller role")
    void storesTheCallerRole() {
        UserContext.setUserRole("ADMIN");

        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("stores and returns the caller email")
    void storesTheCallerEmail() {
        UserContext.setUserEmail("striker@example.com");

        assertThat(UserContext.getUserEmail()).isEqualTo("striker@example.com");
    }

    @Test
    @DisplayName("the last write wins")
    void theLastWriteWins() {
        UserContext.setUserId("1");
        UserContext.setUserId("2");

        assertThat(UserContext.getUserId()).isEqualTo("2");
    }

    @Test
    @DisplayName("clear() removes the id")
    void clearRemovesTheId() {
        UserContext.setUserId("42");

        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("clear() only removes the id - role and email survive, which is a leak across requests")
    void clearOnlyRemovesTheId() {
        UserContext.setUserId("42");
        UserContext.setUserRole("ADMIN");
        UserContext.setUserEmail("striker@example.com");

        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
        assertThat(UserContext.getUserEmail()).isEqualTo("striker@example.com");
    }

    @Test
    @DisplayName("every thread gets its own copy")
    void everyThreadGetsItsOwnCopy() throws Exception {
        UserContext.setUserId("42");
        UserContext.setUserRole("USER");
        UserContext.setUserEmail("striker@example.com");

        AtomicReference<String> otherId = new AtomicReference<>("unset");
        AtomicReference<String> otherRole = new AtomicReference<>("unset");
        AtomicReference<String> otherEmail = new AtomicReference<>("unset");
        CountDownLatch done = new CountDownLatch(1);

        Thread other = new Thread(() -> {
            otherId.set(UserContext.getUserId());
            otherRole.set(UserContext.getUserRole());
            otherEmail.set(UserContext.getUserEmail());

            UserContext.setUserId("7");
            done.countDown();
        });

        other.start();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        other.join();

        assertThat(otherId.get()).isNull();
        assertThat(otherRole.get()).isNull();
        assertThat(otherEmail.get()).isNull();

        // the other thread's write did not bleed into this one
        assertThat(UserContext.getUserId()).isEqualTo("42");
    }
}


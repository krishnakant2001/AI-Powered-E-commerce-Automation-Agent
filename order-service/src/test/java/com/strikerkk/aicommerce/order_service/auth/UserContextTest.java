package com.strikerkk.aicommerce.order_service.auth;

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
    void clear() {
        UserContext.clear();
    }

    @Test
    @DisplayName("remembers the caller id for the rest of the request")
    void shouldRememberTheCallerId() {
        UserContext.setUserId("42");

        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("remembers the caller role as well")
    void shouldRememberTheCallerRole() {
        UserContext.setUserRole("ADMIN");

        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("is empty before anything is set")
    void shouldBeEmptyByDefault() {
        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("keeps the id and the role apart")
    void shouldKeepTheIdAndTheRoleApart() {
        UserContext.setUserId("42");
        UserContext.setUserRole("USER");

        assertThat(UserContext.getUserId()).isEqualTo("42");
        assertThat(UserContext.getUserRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("the last value wins")
    void theLastValueWins() {
        UserContext.setUserId("42");
        UserContext.setUserId("99");

        assertThat(UserContext.getUserId()).isEqualTo("99");
    }

    @Test
    @DisplayName("accepts a null value without blowing up")
    void shouldAcceptNull() {
        UserContext.setUserId(null);
        UserContext.setUserRole(null);

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("clear() wipes both values, so a pooled thread never leaks an identity")
    void clearWipesBothValues() {
        UserContext.setUserId("42");
        UserContext.setUserRole("USER");

        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("clear() on an empty context is harmless")
    void clearIsIdempotent() {
        UserContext.clear();
        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("one thread never sees the identity of another")
    void isThreadConfined() throws Exception {
        UserContext.setUserId("42");
        UserContext.setUserRole("USER");

        AtomicReference<String> otherId = new AtomicReference<>("untouched");
        AtomicReference<String> otherRole = new AtomicReference<>("untouched");
        CountDownLatch done = new CountDownLatch(1);

        Thread other = new Thread(() -> {
            otherId.set(UserContext.getUserId());
            otherRole.set(UserContext.getUserRole());
            UserContext.setUserId("99");
            done.countDown();
        });
        other.start();

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        other.join();

        assertThat(otherId.get()).isNull();
        assertThat(otherRole.get()).isNull();
        // The other thread did not overwrite ours either.
        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("exposes only static helpers - it is never injected")
    void isAStaticHelper() {
        assertThat(java.util.Arrays.stream(UserContext.class.getDeclaredMethods())
                .allMatch(method -> java.lang.reflect.Modifier.isStatic(method.getModifiers())))
                .isTrue();
    }
}


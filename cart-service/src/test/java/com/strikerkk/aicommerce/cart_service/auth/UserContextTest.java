package com.strikerkk.aicommerce.cart_service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserContext")
class UserContextTest {

    @BeforeEach
    @AfterEach
    void reset() {
        UserContext.clear();
    }

    @Test
    @DisplayName("returns null when nothing was set")
    void shouldReturnNullByDefault() {
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("stores and returns the caller id")
    void shouldStoreTheUserId() {
        UserContext.setUserId("42");

        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("the last write wins")
    void shouldOverwriteThePreviousValue() {
        UserContext.setUserId("42");
        UserContext.setUserId("99");

        assertThat(UserContext.getUserId()).isEqualTo("99");
    }

    @Test
    @DisplayName("clear() removes the value")
    void shouldClearTheUserId() {
        UserContext.setUserId("42");

        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("clear() is idempotent")
    void clearIsIdempotent() {
        UserContext.clear();
        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("an explicit null can be stored")
    void shouldAcceptANullValue() {
        UserContext.setUserId("42");

        UserContext.setUserId(null);

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("two threads never see each other's caller id")
    void shouldIsolateThreads() throws Exception {
        UserContext.setUserId("main-thread");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> other = executor.submit(() -> {
                // the worker thread starts empty ...
                String before = UserContext.getUserId();
                UserContext.setUserId("worker-thread");
                String after = UserContext.getUserId();
                UserContext.clear();
                return before + "|" + after;
            });

            assertThat(other.get(5, TimeUnit.SECONDS)).isEqualTo("null|worker-thread");
        } finally {
            executor.shutdownNow();
        }

        // ... and it never overwrote the value of this thread
        assertThat(UserContext.getUserId()).isEqualTo("main-thread");
    }

    @Test
    @DisplayName("clearing on one thread leaves the other thread untouched")
    void clearIsThreadScoped() throws Exception {
        UserContext.setUserId("main-thread");

        Thread worker = new Thread(UserContext::clear);
        worker.start();
        worker.join(5_000);

        assertThat(UserContext.getUserId()).isEqualTo("main-thread");
    }
}


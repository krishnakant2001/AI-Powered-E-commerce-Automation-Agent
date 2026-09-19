package com.strikerkk.aicommerce.product_service.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserContext")
class UserContextTest {

    @BeforeEach
    @AfterEach
    void reset() {
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    @Test
    @DisplayName("stores and returns the caller id")
    void shouldStoreTheUserId() {
        UserContext.setUserId("admin-1");

        assertThat(UserContext.getUserId()).isEqualTo("admin-1");
    }

    @Test
    @DisplayName("stores and returns the caller role")
    void shouldStoreTheUserRole() {
        UserContext.setUserRole("ADMIN");

        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("returns null when nothing has been stored")
    void shouldReturnNullWhenEmpty() {
        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserRole()).isNull();
    }

    @Test
    @DisplayName("the last write wins")
    void shouldOverwriteThePreviousValue() {
        UserContext.setUserId("admin-1");
        UserContext.setUserId("admin-2");

        assertThat(UserContext.getUserId()).isEqualTo("admin-2");
    }

    @Test
    @DisplayName("clear() removes the caller id")
    void clearRemovesTheUserId() {
        UserContext.setUserId("admin-1");

        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("clear() does NOT remove the caller role - the role leaks into the next request")
    void clearDoesNotRemoveTheUserRole() {
        UserContext.setUserId("admin-1");
        UserContext.setUserRole("ADMIN");

        UserContext.clear();

        // Documents the current behaviour: UserContext.clear() only calls userId.remove().
        // Because the servlet container reuses its threads, the role of a previous request stays
        // visible to the next one. See src/test/README.md - "Notes / findings".
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("every thread gets its own value")
    void shouldBeIsolatedPerThread() throws Exception {
        UserContext.setUserId("main-thread");

        AtomicReference<String> seenByOtherThread = new AtomicReference<>("not-set");
        CountDownLatch latch = new CountDownLatch(1);

        Thread other = new Thread(() -> {
            seenByOtherThread.set(UserContext.getUserId());
            UserContext.setUserId("other-thread");
            latch.countDown();
        });
        other.start();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        other.join();

        assertThat(seenByOtherThread.get()).isNull();
        assertThat(UserContext.getUserId()).isEqualTo("main-thread");
    }

    @Test
    @DisplayName("the id and the role are stored independently")
    void idAndRoleAreIndependent() {
        UserContext.setUserId("admin-1");
        UserContext.setUserRole("ADMIN");

        UserContext.setUserId("admin-2");

        assertThat(UserContext.getUserId()).isEqualTo("admin-2");
        assertThat(UserContext.getUserRole()).isEqualTo("ADMIN");
    }
}


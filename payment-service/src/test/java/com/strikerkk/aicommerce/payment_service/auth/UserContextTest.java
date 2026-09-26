package com.strikerkk.aicommerce.payment_service.auth;

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
    @DisplayName("hands back the id that was put in")
    void handsBackTheId() {
        UserContext.setUserId("42");

        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("is empty before the interceptor ran")
    void isEmptyBeforeTheInterceptorRan() {
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("the last write wins")
    void theLastWriteWins() {
        UserContext.setUserId("42");
        UserContext.setUserId("99");

        assertThat(UserContext.getUserId()).isEqualTo("99");
    }

    @Test
    @DisplayName("is emptied by clear, so a pooled thread never serves the wrong customer")
    void isEmptiedByClear() {
        UserContext.setUserId("42");

        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("clearing twice is harmless")
    void clearingTwiceIsHarmless() {
        UserContext.setUserId("42");

        UserContext.clear();
        UserContext.clear();

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("accepts a null id without blowing up")
    void acceptsANullId() {
        UserContext.setUserId(null);

        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("keeps every request on its own thread - two customers never mix")
    void keepsEveryRequestOnItsOwnThread() throws InterruptedException {
        UserContext.setUserId("42");

        AtomicReference<String> seenByAnotherThread = new AtomicReference<>();
        Thread other = new Thread(() -> {
            UserContext.setUserId("99");
            seenByAnotherThread.set(UserContext.getUserId());
            UserContext.clear();
        });
        other.start();
        other.join();

        assertThat(seenByAnotherThread.get()).isEqualTo("99");
        assertThat(UserContext.getUserId()).isEqualTo("42");
    }

    @Test
    @DisplayName("a fresh thread starts empty, it never inherits the caller")
    void aFreshThreadStartsEmpty() throws InterruptedException {
        UserContext.setUserId("42");

        AtomicReference<String> seenByAChildThread = new AtomicReference<>("not-read-yet");
        Thread child = new Thread(() -> seenByAChildThread.set(UserContext.getUserId()));
        child.start();
        child.join();

        assertThat(seenByAChildThread.get()).isNull();
    }

    @Test
    @DisplayName("offers only the three static operations the interceptors need")
    void offersOnlyThreeOperations() {
        assertThat(UserContext.class.getDeclaredMethods())
                .filteredOn(m -> !m.isSynthetic())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("setUserId", "getUserId", "clear");
    }

    @Test
    @DisplayName("keeps the holder private and static, there is a single one per JVM")
    void keepsTheHolderPrivateAndStatic() throws Exception {
        java.lang.reflect.Field holder = UserContext.class.getDeclaredField("userId");

        assertThat(java.lang.reflect.Modifier.isPrivate(holder.getModifiers())).isTrue();
        assertThat(java.lang.reflect.Modifier.isStatic(holder.getModifiers())).isTrue();
        assertThat(java.lang.reflect.Modifier.isFinal(holder.getModifiers())).isTrue();
        assertThat(holder.getType()).isEqualTo(ThreadLocal.class);
    }
}




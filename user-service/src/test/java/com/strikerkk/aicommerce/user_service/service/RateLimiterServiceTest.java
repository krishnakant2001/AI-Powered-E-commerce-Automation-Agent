package com.strikerkk.aicommerce.user_service.service;

import com.strikerkk.aicommerce.user_service.exception.TooManyAttemptsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiterService")
class RateLimiterServiceTest {

    private static final String KEY = "login:email:krishna@example.com";
    private static final String REDIS_KEY = "rate_limit:" + KEY;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(60);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @Captor
    private ArgumentCaptor<Duration> durationCaptor;

    /** Stubs the counter Redis returns for {@code key} once it has been incremented. */
    private void givenAttemptCount(String key, Long count) {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rate_limit:" + key)).thenReturn(count);
    }

    @Nested
    @DisplayName("allowAndRecordAttempts")
    class AllowAndRecordAttempts {

        @Test
        @DisplayName("increments a counter stored under the 'rate_limit:' prefixed key")
        void shouldIncrementPrefixedKey() {
            givenAttemptCount(KEY, 1L);

            rateLimiterService.allowAndRecordAttempts(KEY);

            verify(valueOperations).increment(REDIS_KEY);
        }

        @Test
        @DisplayName("starts a 60 minute window on the very first attempt")
        void shouldStartTheWindowOnTheFirstAttempt() {
            givenAttemptCount(KEY, 1L);

            rateLimiterService.allowAndRecordAttempts(KEY);

            verify(stringRedisTemplate).expire(eq(REDIS_KEY), durationCaptor.capture());
            assertThat(durationCaptor.getValue()).isEqualTo(WINDOW);
        }

        @ParameterizedTest(name = "attempt #{0} does not reset the TTL")
        @ValueSource(longs = {2L, 3L, 4L, 5L})
        @DisplayName("keeps the original window for every subsequent attempt")
        void shouldNotRefreshTheWindowOnSubsequentAttempts(long count) {
            givenAttemptCount(KEY, count);

            rateLimiterService.allowAndRecordAttempts(KEY);

            verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @ParameterizedTest(name = "attempt #{0} is allowed")
        @ValueSource(longs = {1L, 2L, 3L, 4L, 5L})
        @DisplayName("allows every attempt up to and including the 5th one")
        void shouldAllowAttemptsWithinTheLimit(long count) {
            givenAttemptCount(KEY, count);

            assertThatCode(() -> rateLimiterService.allowAndRecordAttempts(KEY))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects the attempt right after the limit has been reached")
        void shouldRejectTheAttemptAfterTheLimit() {
            givenAttemptCount(KEY, (long) MAX_ATTEMPTS + 1);

            assertThatThrownBy(() -> rateLimiterService.allowAndRecordAttempts(KEY))
                    .isInstanceOf(TooManyAttemptsException.class)
                    .hasMessage("Too many login attempts. Try again later.");
        }

        @Test
        @DisplayName("keeps rejecting while the counter stays above the limit")
        void shouldKeepRejectingWhileOverTheLimit() {
            givenAttemptCount(KEY, 42L);

            assertThatThrownBy(() -> rateLimiterService.allowAndRecordAttempts(KEY))
                    .isInstanceOf(TooManyAttemptsException.class);

            verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("fails open when Redis returns no counter value")
        void shouldFailOpenWhenRedisReturnsNull() {
            givenAttemptCount(KEY, null);

            assertThatCode(() -> rateLimiterService.allowAndRecordAttempts(KEY))
                    .doesNotThrowAnyException();

            verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("never deletes the counter while recording an attempt")
        void shouldNotResetWhileRecording() {
            givenAttemptCount(KEY, 3L);

            rateLimiterService.allowAndRecordAttempts(KEY);

            verify(stringRedisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("isolates the counters of two different keys")
        void shouldIsolateCountersPerKey() {
            String otherKey = "login:email:someone-else@example.com";
            givenAttemptCount(KEY, (long) MAX_ATTEMPTS + 1);
            when(valueOperations.increment("rate_limit:" + otherKey)).thenReturn(1L);

            assertThatThrownBy(() -> rateLimiterService.allowAndRecordAttempts(KEY))
                    .isInstanceOf(TooManyAttemptsException.class);
            assertThatCode(() -> rateLimiterService.allowAndRecordAttempts(otherKey))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("resetAttempts")
    class ResetAttempts {

        @Test
        @DisplayName("deletes the counter stored under the prefixed key")
        void shouldDeleteThePrefixedKey() {
            rateLimiterService.resetAttempts(KEY);

            verify(stringRedisTemplate).delete(REDIS_KEY);
        }

        @Test
        @DisplayName("does not touch the counter value operations")
        void shouldNotIncrementOrExpire() {
            rateLimiterService.resetAttempts(KEY);

            verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
            verifyNoInteractions(valueOperations);
        }
    }
}


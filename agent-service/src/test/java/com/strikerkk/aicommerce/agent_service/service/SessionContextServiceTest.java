package com.strikerkk.aicommerce.agent_service.service;

import com.strikerkk.aicommerce.agent_service.model.ConversationMessage;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionContextService")
class SessionContextServiceTest {

    private static final UUID SESSION_ID = TestDataFactory.SESSION_ID;
    private static final Long USER_ID = TestDataFactory.USER_ID;
    private static final String SESSION_KEY = "session:" + TestDataFactory.SESSION_ID;
    private static final String USER_KEY = "user:session:" + TestDataFactory.USER_ID;
    private static final long TTL_MINUTES = 60L;

    @Mock
    private RedisTemplate<String, SessionContext> sessionContextRedisTemplate;

    @Mock
    private ValueOperations<String, SessionContext> valueOperations;

    @InjectMocks
    private SessionContextService sessionContextService;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<Duration> ttlCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sessionContextService, "ttlMinutes", TTL_MINUTES);
    }

    private void redisReturnsValueOperations() {
        when(sessionContextRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("writes the context under both the session key and the user key")
        void writesBothKeys() {
            redisReturnsValueOperations();
            SessionContext context = TestDataFactory.context();

            sessionContextService.save(context);

            verify(valueOperations, times(2))
                    .set(keyCaptor.capture(), eq(context), any(Duration.class));

            assertThat(keyCaptor.getAllValues()).containsExactly(SESSION_KEY, USER_KEY);
        }

        @Test
        @DisplayName("applies the configured TTL to both keys")
        void appliesTheConfiguredTtl() {
            redisReturnsValueOperations();

            sessionContextService.save(TestDataFactory.context());

            verify(valueOperations, times(2)).set(any(), any(), ttlCaptor.capture());

            assertThat(ttlCaptor.getAllValues())
                    .containsExactly(Duration.ofMinutes(TTL_MINUTES), Duration.ofMinutes(TTL_MINUTES));
        }

        @Test
        @DisplayName("stamps lastActivityAt so an idle session can be spotted")
        void stampsLastActivity() {
            redisReturnsValueOperations();
            SessionContext context = TestDataFactory.context();
            context.setLastActivityAt(LocalDateTime.of(2020, 1, 1, 0, 0));

            LocalDateTime before = LocalDateTime.now().minusSeconds(1);
            sessionContextService.save(context);

            assertThat(context.getLastActivityAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("a changed TTL is honoured")
        void aChangedTtlIsHonoured() {
            redisReturnsValueOperations();
            ReflectionTestUtils.setField(sessionContextService, "ttlMinutes", 5L);

            sessionContextService.save(TestDataFactory.context());

            verify(valueOperations, times(2)).set(any(), any(), eq(Duration.ofMinutes(5)));
        }
    }

    @Nested
    @DisplayName("findBySessionId")
    class FindBySessionId {

        @Test
        @DisplayName("reads the session key and wraps the hit")
        void wrapsTheHit() {
            redisReturnsValueOperations();
            SessionContext stored = TestDataFactory.context();
            when(valueOperations.get(SESSION_KEY)).thenReturn(stored);

            Optional<SessionContext> found = sessionContextService.findBySessionId(SESSION_ID);

            assertThat(found).containsSame(stored);
        }

        @Test
        @DisplayName("an expired key yields Optional.empty rather than null")
        void anExpiredKeyYieldsEmpty() {
            redisReturnsValueOperations();
            when(valueOperations.get(SESSION_KEY)).thenReturn(null);

            assertThat(sessionContextService.findBySessionId(SESSION_ID)).isEmpty();
        }

        @Test
        @DisplayName("builds the key as session:{sessionId}")
        void buildsTheKey() {
            redisReturnsValueOperations();
            UUID other = UUID.fromString("55555555-5555-5555-5555-555555555555");

            sessionContextService.findBySessionId(other);

            verify(valueOperations).get("session:55555555-5555-5555-5555-555555555555");
        }
    }

    @Nested
    @DisplayName("findByActiveSessionUserId")
    class FindByActiveSessionUserId {

        @Test
        @DisplayName("reads the user key and wraps the hit")
        void wrapsTheHit() {
            redisReturnsValueOperations();
            SessionContext stored = TestDataFactory.context();
            when(valueOperations.get(USER_KEY)).thenReturn(stored);

            assertThat(sessionContextService.findByActiveSessionUserId(USER_ID)).containsSame(stored);
        }

        @Test
        @DisplayName("a user with no live session yields Optional.empty")
        void aUserWithNoLiveSessionYieldsEmpty() {
            redisReturnsValueOperations();
            when(valueOperations.get(USER_KEY)).thenReturn(null);

            assertThat(sessionContextService.findByActiveSessionUserId(USER_ID)).isEmpty();
        }

        @Test
        @DisplayName("builds the key as user:session:{userId}")
        void buildsTheKey() {
            redisReturnsValueOperations();

            sessionContextService.findByActiveSessionUserId(99L);

            verify(valueOperations).get("user:session:99");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("removes both keys, so the user really has no live session left")
        void removesBothKeys() {
            sessionContextService.delete(SESSION_ID, USER_ID);

            verify(sessionContextRedisTemplate).delete(SESSION_KEY);
            verify(sessionContextRedisTemplate).delete(USER_KEY);
            verifyNoMoreInteractions(sessionContextRedisTemplate);
        }

        @Test
        @DisplayName("never needs the value operations")
        void neverNeedsTheValueOperations() {
            sessionContextService.delete(SESSION_ID, USER_ID);

            verify(sessionContextRedisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("appendMessage")
    class AppendMessage {

        @Test
        @DisplayName("appends the turn and writes the context back")
        void appendsAndWritesBack() {
            redisReturnsValueOperations();
            SessionContext stored = TestDataFactory.contextWith(TestDataFactory.userTurn("hi"));
            when(valueOperations.get(SESSION_KEY)).thenReturn(stored);

            sessionContextService.appendMessage(SESSION_ID, TestDataFactory.assistantTurn("hello"));

            assertThat(stored.getConversationMessages())
                    .extracting(ConversationMessage::getContent)
                    .containsExactly("hi", "hello");

            verify(valueOperations, times(2)).set(any(), eq(stored), any(Duration.class));
        }

        @Test
        @DisplayName("does nothing when the context has already expired")
        void doesNothingWhenExpired() {
            redisReturnsValueOperations();
            when(valueOperations.get(SESSION_KEY)).thenReturn(null);

            sessionContextService.appendMessage(SESSION_ID, TestDataFactory.assistantTurn("hello"));

            verify(valueOperations, never()).set(any(), any(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("refreshTTL")
    class RefreshTtl {

        @Test
        @DisplayName("pushes the expiry out without rewriting the value")
        void pushesTheExpiryOut() {
            sessionContextService.refreshTTL(SESSION_ID);

            verify(sessionContextRedisTemplate).expire(SESSION_KEY, Duration.ofMinutes(TTL_MINUTES));
            verify(sessionContextRedisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("only touches the session key, not the user key")
        void onlyTouchesTheSessionKey() {
            sessionContextService.refreshTTL(SESSION_ID);

            verify(sessionContextRedisTemplate, never()).expire(eq(USER_KEY), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("reports true when the session key is still alive")
        void reportsTrue() {
            when(sessionContextRedisTemplate.hasKey(SESSION_KEY)).thenReturn(true);

            assertThat(sessionContextService.exists(SESSION_ID)).isTrue();
        }

        @Test
        @DisplayName("reports false once the key expired")
        void reportsFalse() {
            when(sessionContextRedisTemplate.hasKey(SESSION_KEY)).thenReturn(false);

            assertThat(sessionContextService.exists(SESSION_ID)).isFalse();
        }
    }
}


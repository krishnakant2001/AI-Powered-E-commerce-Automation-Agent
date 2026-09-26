package com.strikerkk.aicommerce.agent_service.support;

import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * A {@link RedisTemplate} that keeps everything in a {@link ConcurrentHashMap}.
 * <p>
 * Lets the integration tests exercise the real {@code SessionContextService} without
 * asking the build machine for a Redis server. TTLs are accepted and ignored.
 */
public final class InMemorySessionContextRedis {

    private InMemorySessionContextRedis() {
    }

    @SuppressWarnings("unchecked")
    public static RedisTemplate<String, SessionContext> create(Map<String, SessionContext> store) {
        return applyTo(Mockito.mock(RedisTemplate.class), store);
    }

    /**
     * Teaches an already-created {@link RedisTemplate} mock - for instance one produced by
     * {@code @MockitoBean} - to behave like a map. Call this from {@code @BeforeEach}, because
     * Spring resets bean-override mocks between tests.
     */
    @SuppressWarnings("unchecked")
    public static RedisTemplate<String, SessionContext> applyTo(RedisTemplate<String, SessionContext> template,
                                                                Map<String, SessionContext> store) {
        ValueOperations<String, SessionContext> valueOperations = Mockito.mock(ValueOperations.class);

        Mockito.when(template.opsForValue()).thenReturn(valueOperations);

        Mockito.doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(SessionContext.class), any(Duration.class));

        Mockito.doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(SessionContext.class));

        Mockito.when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> store.get(invocation.getArgument(0, String.class)));

        Mockito.when(template.delete(anyString()))
                .thenAnswer(invocation -> store.remove(invocation.getArgument(0, String.class)) != null);

        Mockito.when(template.hasKey(anyString()))
                .thenAnswer(invocation -> store.containsKey(invocation.getArgument(0, String.class)));

        Mockito.when(template.expire(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> store.containsKey(invocation.getArgument(0, String.class)));

        return template;
    }

    public static Map<String, SessionContext> newStore() {
        return new ConcurrentHashMap<>();
    }
}



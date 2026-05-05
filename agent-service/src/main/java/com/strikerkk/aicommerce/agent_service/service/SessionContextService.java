package com.strikerkk.aicommerce.agent_service.service;

import com.strikerkk.aicommerce.agent_service.model.ConversationMessage;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionContextService {

    private final RedisTemplate<String, SessionContext> sessionContextRedisTemplate;

    @Value("${agent.session.ttl-minutes}")
    private Long ttlMinutes;
    
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String SESSION_USER_KEY_PREFIX = "user:session:";


    // Save or update sessionContext in redis
    public void save(SessionContext context) {
        String key = buildSessionKey(context.getSessionId());
        context.setLastActivityAt(LocalDateTime.now());

        sessionContextRedisTemplate.opsForValue().set(
                key,
                context,
                Duration.ofMinutes(ttlMinutes)
        );

        String userKey = buildUserSessionKey(context.getUserId());
        context.setLastActivityAt(LocalDateTime.now());

        sessionContextRedisTemplate.opsForValue().set(
                userKey,
                context,
                Duration.ofMinutes(ttlMinutes)
        );

        log.debug("Saved session context to Redis - key={} ttl={}", key, ttlMinutes);
    }


    // Get sessionContext by sessionId
    public Optional<SessionContext> findBySessionId(UUID sessionId) {
        String key = buildSessionKey(sessionId);
        SessionContext context = sessionContextRedisTemplate.opsForValue().get(key);

        if(context == null) {
            log.debug("Session context not found in Redis - key={}", key);
            return Optional.empty();
        }

        return Optional.of(context);
    }

    // Get activeSessionContext by userId
    public Optional<SessionContext> findByActiveSessionUserId(Long userId) {
        String userKey = buildUserSessionKey(userId);
        SessionContext context = sessionContextRedisTemplate.opsForValue().get(userKey);

        if(context == null) {
            return Optional.empty();
        }

        return Optional.of(context);
    }


    // Delete sessionContext from redis
    public void delete(UUID sessionId, Long userId) {
        String sessionKey = buildSessionKey(sessionId);
        String userKey = buildUserSessionKey(userId);

        sessionContextRedisTemplate.delete(sessionKey);
        sessionContextRedisTemplate.delete(userKey);

        log.debug("Deleted Session context from Redis - sessionId={}", sessionId);
    }


    // Append a new message turn to the conversation history
    public void appendMessage(UUID sessionId, ConversationMessage message) {
        findBySessionId(sessionId).ifPresent(context -> {
            context.getConversationMessages().add(message);
            save(context);

            log.debug("Append message to session={}, role={}", sessionId, message.getRole());
        });
    }


    // Reset TTL without changing context, key expire after this much time from now
    // Called on every /chat request to keep active sessions alive
    public void refreshTTL(UUID sessionId) {
        String key = buildSessionKey(sessionId);
        sessionContextRedisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));

        log.debug("Refreshed TTL for sessionId={}", sessionId);

    }


    // Check if session exists in redis
    public Boolean exists(UUID sessionId) {
        return sessionContextRedisTemplate.hasKey(buildSessionKey(sessionId));
    }


    private String buildSessionKey(UUID sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String buildUserSessionKey(Long userId) {
        return SESSION_USER_KEY_PREFIX + userId;
    }
}

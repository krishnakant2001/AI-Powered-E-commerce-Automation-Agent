package com.strikerkk.aicommerce.agent_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.strikerkk.aicommerce.agent_service.model.ConversationMessage;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("RedisConfig")
class RedisConfigTest {

    private final RedisConfig redisConfig = new RedisConfig();
    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

    @SuppressWarnings("unchecked")
    private RedisSerializer<SessionContext> sessionSerializer(RedisTemplate<String, SessionContext> template) {
        return (RedisSerializer<SessionContext>) template.getValueSerializer();
    }

    private SessionContext sampleContext() {
        return SessionContext.builder()
                .sessionId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .userId(42L)
                .userEmail("striker@example.com")
                .currentIntent("buy a speaker")
                .pendingClarificationFor("COLOR")
                .lastProductId("1")
                .lastActivityAt(LocalDateTime.of(2026, 1, 1, 10, 30))
                .conversationMessages(new ArrayList<>(List.of(
                        ConversationMessage.builder().role("user").content("hi").build())))
                .build();
    }

    // wiring ------------------------------------------------------------------

    @Test
    @DisplayName("is a Spring configuration class")
    void isAConfiguration() {
        assertThat(RedisConfig.class.getAnnotation(Configuration.class)).isNotNull();
    }

    @Test
    @DisplayName("exposes three beans")
    void exposesThreeBeans() {
        assertThat(RedisConfig.class.getDeclaredMethods())
                .filteredOn(method -> method.getAnnotation(Bean.class) != null)
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("redisTemplate", "sessionContextRedisTemplate", "objectMapper");
    }

    // the generic template ------------------------------------------------------------------

    @Test
    @DisplayName("the generic template is wired to the connection factory and initialised")
    void theGenericTemplateIsInitialised() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.isExposeConnection()).isFalse();
    }

    @Test
    @DisplayName("the generic template writes plain-string keys and JSON values")
    void theGenericTemplateUsesStringKeysAndJsonValues() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
    }

    @Test
    @DisplayName("keys land in Redis as readable strings, not as Java serialisation blobs")
    void keysAreReadable() {
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        @SuppressWarnings("unchecked")
        RedisSerializer<String> keySerializer = (RedisSerializer<String>) template.getKeySerializer();

        byte[] raw = keySerializer.serialize("session:abc");

        assertThat(new String(raw, StandardCharsets.UTF_8)).isEqualTo("session:abc");
    }

    // the SessionContext template ------------------------------------------------------------------

    @Test
    @DisplayName("the SessionContext template is wired to the connection factory")
    void theSessionTemplateIsWired() {
        RedisTemplate<String, SessionContext> template =
                redisConfig.sessionContextRedisTemplate(connectionFactory);

        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
    }

    @Test
    @DisplayName("a SessionContext survives the round trip through its serialiser")
    void aSessionContextSurvivesTheRoundTrip() {
        RedisSerializer<SessionContext> serializer =
                sessionSerializer(redisConfig.sessionContextRedisTemplate(connectionFactory));

        SessionContext restored = serializer.deserialize(serializer.serialize(sampleContext()));

        assertThat(restored).isNotNull();
        assertThat(restored.getSessionId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(restored.getUserId()).isEqualTo(42L);
        assertThat(restored.getUserEmail()).isEqualTo("striker@example.com");
        assertThat(restored.getPendingClarificationFor()).isEqualTo("COLOR");
        assertThat(restored.getLastProductId()).isEqualTo("1");
        assertThat(restored.getLastActivityAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 30));
        assertThat(restored.getConversationMessages()).singleElement()
                .satisfies(message -> assertThat(message.getContent()).isEqualTo("hi"));
    }

    @Test
    @DisplayName("the stored JSON is human readable - no type header, ISO dates")
    void theStoredJsonIsReadable() {
        RedisSerializer<SessionContext> serializer =
                sessionSerializer(redisConfig.sessionContextRedisTemplate(connectionFactory));

        String json = new String(serializer.serialize(sampleContext()), StandardCharsets.UTF_8);

        assertThat(json).contains("\"userEmail\":\"striker@example.com\"");
        assertThat(json).contains("2026-01-01T10:30");
        assertThat(json).doesNotContain("@class");
    }

    @Test
    @DisplayName("a null value serialises to an empty array and back to null")
    void aNullValueIsHandled() {
        RedisSerializer<SessionContext> serializer =
                sessionSerializer(redisConfig.sessionContextRedisTemplate(connectionFactory));

        assertThat(serializer.serialize(null)).isEmpty();
        assertThat(serializer.deserialize(new byte[0])).isNull();
        assertThat(serializer.deserialize(null)).isNull();
    }

    // the ObjectMapper ------------------------------------------------------------------

    @Test
    @DisplayName("the shared ObjectMapper understands java.time and writes ISO dates")
    void theObjectMapperUnderstandsJavaTime() throws Exception {
        ObjectMapper objectMapper = redisConfig.objectMapper();

        String json = objectMapper.writeValueAsString(LocalDateTime.of(2026, 1, 1, 10, 30));

        assertThat(json).isEqualTo("\"2026-01-01T10:30:00\"");
        assertThat(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
    }

    @Test
    @DisplayName("the shared ObjectMapper can read a LocalDateTime back")
    void theObjectMapperCanReadDatesBack() throws Exception {
        ObjectMapper objectMapper = redisConfig.objectMapper();

        assertThat(objectMapper.readValue("\"2026-01-01T10:30:00\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 30));
    }
}



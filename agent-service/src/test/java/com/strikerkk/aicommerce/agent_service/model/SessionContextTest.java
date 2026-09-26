package com.strikerkk.aicommerce.agent_service.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Redis models")
class SessionContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Nested
    @DisplayName("SessionContext")
    class SessionContextModel {

        @Test
        @DisplayName("is serialisable, as Redis value serialisers expect")
        void isSerialisable() {
            assertThat(Serializable.class).isAssignableFrom(SessionContext.class);
        }

        @Test
        @DisplayName("starts with an empty but mutable conversation")
        void startsWithAnEmptyMutableConversation() {
            SessionContext context = SessionContext.builder().sessionId(UUID.randomUUID()).userId(42L).build();

            assertThat(context.getConversationMessages()).isEmpty();

            context.getConversationMessages().add(ConversationMessage.builder().role("user").content("hi").build());

            assertThat(context.getConversationMessages()).hasSize(1);
        }

        @Test
        @DisplayName("the no-arg constructor also yields an empty conversation")
        void theNoArgConstructorYieldsAnEmptyConversation() {
            assertThat(new SessionContext().getConversationMessages()).isEmpty();
        }

        @Test
        @DisplayName("remembers everything the agent needs between turns")
        void remembersEverythingBetweenTurns() {
            UUID sessionId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            SessionContext context = SessionContext.builder()
                    .sessionId(sessionId)
                    .userId(42L)
                    .userEmail("striker@example.com")
                    .currentIntent("buy a speaker")
                    .pendingClarificationFor("COLOR")
                    .lastProductId("1")
                    .lastVariantId("2")
                    .lastOrderId("1001")
                    .lastActivityAt(now)
                    .build();

            assertThat(context.getSessionId()).isEqualTo(sessionId);
            assertThat(context.getUserId()).isEqualTo(42L);
            assertThat(context.getUserEmail()).isEqualTo("striker@example.com");
            assertThat(context.getCurrentIntent()).isEqualTo("buy a speaker");
            assertThat(context.getPendingClarificationFor()).isEqualTo("COLOR");
            assertThat(context.getLastProductId()).isEqualTo("1");
            assertThat(context.getLastVariantId()).isEqualTo("2");
            assertThat(context.getLastOrderId()).isEqualTo("1001");
            assertThat(context.getLastActivityAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("the clarification flag can be cleared once the agent answers")
        void theClarificationFlagCanBeCleared() {
            SessionContext context = SessionContext.builder().pendingClarificationFor("SIZE").build();

            context.setPendingClarificationFor(null);

            assertThat(context.getPendingClarificationFor()).isNull();
        }

        @Test
        @DisplayName("survives a JSON round trip - this is literally what Redis stores")
        void survivesAJsonRoundTrip() throws Exception {
            SessionContext original = SessionContext.builder()
                    .sessionId(UUID.randomUUID())
                    .userId(42L)
                    .userEmail("striker@example.com")
                    .currentIntent("buy a speaker")
                    .pendingClarificationFor("COLOR")
                    .lastProductId("1")
                    .lastVariantId("2")
                    .lastOrderId("1001")
                    .lastActivityAt(LocalDateTime.of(2026, 1, 1, 10, 30))
                    .conversationMessages(new java.util.ArrayList<>(List.of(
                            ConversationMessage.builder().role("user").content("hi").build(),
                            ConversationMessage.builder().role("assistant").content("hello").build())))
                    .build();

            String json = objectMapper.writeValueAsString(original);
            SessionContext restored = objectMapper.readValue(json, SessionContext.class);

            assertThat(json).doesNotContain("1767262200"); // not an epoch number
            assertThat(restored.getSessionId()).isEqualTo(original.getSessionId());
            assertThat(restored.getUserId()).isEqualTo(42L);
            assertThat(restored.getUserEmail()).isEqualTo("striker@example.com");
            assertThat(restored.getPendingClarificationFor()).isEqualTo("COLOR");
            assertThat(restored.getLastOrderId()).isEqualTo("1001");
            assertThat(restored.getLastActivityAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 30));
            assertThat(restored.getConversationMessages()).hasSize(2);
            assertThat(restored.getConversationMessages().get(1).getContent()).isEqualTo("hello");
        }

        @Test
        @DisplayName("the all-args constructor keeps the declared field order")
        void theAllArgsConstructorKeepsTheFieldOrder() {
            UUID sessionId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            SessionContext context = new SessionContext(sessionId, 42L, "striker@example.com",
                    new java.util.ArrayList<>(), "intent", "COLOR", "1", "2", "1001", now);

            assertThat(context.getSessionId()).isEqualTo(sessionId);
            assertThat(context.getUserEmail()).isEqualTo("striker@example.com");
            assertThat(context.getCurrentIntent()).isEqualTo("intent");
            assertThat(context.getLastActivityAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("ConversationMessage")
    class ConversationMessageModel {

        @Test
        @DisplayName("is serialisable")
        void isSerialisable() {
            assertThat(Serializable.class).isAssignableFrom(ConversationMessage.class);
        }

        @Test
        @DisplayName("a plain turn carries no tool name")
        void aPlainTurnCarriesNoToolName() {
            ConversationMessage message = ConversationMessage.builder().role("user").content("hi").build();

            assertThat(message.getRole()).isEqualTo("user");
            assertThat(message.getContent()).isEqualTo("hi");
            assertThat(message.getToolName()).isNull();
        }

        @Test
        @DisplayName("a tool result is tagged with the magic \"tool_result\" name")
        void aToolResultIsTagged() {
            ConversationMessage message = ConversationMessage.builder()
                    .role("user")
                    .content("[{\"type\":\"tool_result\"}]")
                    .toolName("tool_result")
                    .build();

            assertThat(message.getToolName()).isEqualTo("tool_result");
        }

        @Test
        @DisplayName("the no-arg constructor and the setters work")
        void theNoArgConstructorWorks() {
            ConversationMessage message = new ConversationMessage();

            message.setRole("assistant");
            message.setContent("hello");
            message.setToolName("addToCart");

            assertThat(message.getRole()).isEqualTo("assistant");
            assertThat(message.getContent()).isEqualTo("hello");
            assertThat(message.getToolName()).isEqualTo("addToCart");
        }

        @Test
        @DisplayName("survives a JSON round trip")
        void survivesAJsonRoundTrip() throws Exception {
            ConversationMessage original = new ConversationMessage("assistant", "hello", null);

            ConversationMessage restored = objectMapper.readValue(
                    objectMapper.writeValueAsString(original), ConversationMessage.class);

            assertThat(restored.getRole()).isEqualTo("assistant");
            assertThat(restored.getContent()).isEqualTo("hello");
            assertThat(restored.getToolName()).isNull();
        }
    }
}


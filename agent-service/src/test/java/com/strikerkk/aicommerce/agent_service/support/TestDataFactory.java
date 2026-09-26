package com.strikerkk.aicommerce.agent_service.support;

import com.strikerkk.aicommerce.agent_service.dto.request.ChatRequest;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import com.strikerkk.aicommerce.agent_service.entity.AgentAction;
import com.strikerkk.aicommerce.agent_service.entity.AgentMessage;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import com.strikerkk.aicommerce.agent_service.model.ConversationMessage;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Every fixture the agent-service tests need, in one place.
 * Nothing here touches Spring - these are plain objects.
 */
public final class TestDataFactory {

    public static final Long USER_ID = 42L;
    public static final Long OTHER_USER_ID = 99L;
    public static final String USER_EMAIL = "striker@example.com";
    public static final String USER_ROLE = "USER";

    public static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTHER_SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID ACTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID MESSAGE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    public static final String INITIAL_INTENT = "I want to buy a bluetooth speaker";

    private TestDataFactory() {
    }

    // entities ------------------------------------------------------------------

    public static AgentSession session() {
        return session(SESSION_ID, USER_ID, SessionStatus.ACTIVE);
    }

    public static AgentSession session(UUID sessionId, Long userId) {
        return session(sessionId, userId, SessionStatus.ACTIVE);
    }

    public static AgentSession session(UUID sessionId, Long userId, SessionStatus status) {
        AgentSession session = AgentSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .status(status)
                .initialIntent(INITIAL_INTENT)
                .build();

        session.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        session.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 5));

        return session;
    }

    public static AgentMessage message(AgentSession session, MessageRole role, String content, int sequence) {
        AgentMessage message = AgentMessage.builder()
                .messageId(UUID.randomUUID())
                .session(session)
                .role(role)
                .content(content)
                .sequenceNumber(sequence)
                .build();

        message.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, sequence % 60));

        return message;
    }

    public static AgentMessage toolMessage(AgentSession session, MessageRole role, String toolName,
                                           String toolInput, String toolOutput, int sequence) {
        AgentMessage message = AgentMessage.builder()
                .messageId(UUID.randomUUID())
                .session(session)
                .role(role)
                .toolName(toolName)
                .toolInput(toolInput)
                .toolOutput(toolOutput)
                .sequenceNumber(sequence)
                .build();

        message.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, sequence % 60));

        return message;
    }

    public static AgentAction action(AgentSession session, ActionType type, ActionStatus status) {
        return action(session, type, status, null);
    }

    public static AgentAction action(AgentSession session, ActionType type, ActionStatus status, String resourceId) {
        AgentAction action = AgentAction.builder()
                .actionId(UUID.randomUUID())
                .session(session)
                .userId(session.getUserId())
                .actionType(type)
                .status(status)
                .requestPayload("{\"productId\":1}")
                .responsePayload("{\"ok\":true}")
                .resourceId(resourceId)
                .build();

        action.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 1));
        action.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 2));

        return action;
    }

    public static AgentAction pendingAction() {
        AgentAction action = action(session(), ActionType.ADD_TO_CART, ActionStatus.PENDING);
        action.setActionId(ACTION_ID);
        return action;
    }

    // redis model ------------------------------------------------------------------

    public static SessionContext context() {
        return context(SESSION_ID, USER_ID);
    }

    public static SessionContext context(UUID sessionId, Long userId) {
        return SessionContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .userEmail(USER_EMAIL)
                .currentIntent(INITIAL_INTENT)
                .conversationMessages(new ArrayList<>())
                .lastActivityAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }

    public static SessionContext contextWith(ConversationMessage... messages) {
        SessionContext context = context();
        context.getConversationMessages().addAll(Arrays.asList(messages));
        return context;
    }

    public static ConversationMessage userTurn(String content) {
        return ConversationMessage.builder().role("user").content(content).build();
    }

    public static ConversationMessage assistantTurn(String content) {
        return ConversationMessage.builder().role("assistant").content(content).build();
    }

    public static ConversationMessage toolResultTurn(String content) {
        return ConversationMessage.builder().role("user").content(content).toolName("tool_result").build();
    }

    // requests ------------------------------------------------------------------

    public static ChatRequest chatRequest(UUID sessionId, String message) {
        ChatRequest request = new ChatRequest();
        request.setSessionId(sessionId);
        request.setMessage(message);
        return request;
    }

    public static StartSessionRequest startSessionRequest(String intent) {
        StartSessionRequest request = new StartSessionRequest();
        request.setInitialIntent(intent);
        return request;
    }

    // ---------------------------------------------------------------------------
    // Claude (Anthropic Messages API) response payloads
    // ---------------------------------------------------------------------------

    /** A plain text answer that ends the turn. */
    public static String llmEndTurn(String text) {
        return """
                {
                  "id": "msg_01",
                  "type": "message",
                  "role": "assistant",
                  "stop_reason": "end_turn",
                  "content": [ { "type": "text", "text": "%s" } ]
                }
                """.formatted(escape(text));
    }

    /** The model asks a clarifying question and pauses. */
    public static String llmPauseTurn(String text) {
        return """
                {
                  "stop_reason": "pause_turn",
                  "content": [ { "type": "text", "text": "%s" } ]
                }
                """.formatted(escape(text));
    }

    /** The model asks for one tool to be executed. */
    public static String llmToolUse(String toolUseId, String toolName, String inputJson) {
        return """
                {
                  "stop_reason": "tool_use",
                  "content": [
                    { "type": "text", "text": "let me check that for you" },
                    { "type": "tool_use", "id": "%s", "name": "%s", "input": %s }
                  ]
                }
                """.formatted(toolUseId, toolName, inputJson);
    }

    /** Anything the orchestrator does not know how to handle. */
    public static String llmUnknownStopReason() {
        return """
                {
                  "stop_reason": "max_tokens",
                  "content": [ { "type": "text", "text": "truncated" } ]
                }
                """;
    }

    /** A tool answer that the orchestrator reads as a failure. */
    public static String toolFailure(String toolName, String message) {
        return "{\"error\": true, \"tool\": \"%s\", \"message\": \"%s\"}".formatted(toolName, message);
    }

    public static List<String> allToolNames() {
        return List.of("searchProducts", "getProductDetails", "getVariantInfo", "getCart", "addToCart",
                "clearCart", "buyNow", "placeOrder", "getOrder", "getMyOrders", "initiatePayment",
                "getAllAddresses");
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}


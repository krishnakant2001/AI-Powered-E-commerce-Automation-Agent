package com.strikerkk.aicommerce.agent_service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.agent_service.dto.response.ActionResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.ChatResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.ConversationHistoryResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.MessageResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.MySessionResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.SessionActionResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.SessionStatusResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.StartSessionResponse;
import com.strikerkk.aicommerce.agent_service.dto.summary.ActionSummary;
import com.strikerkk.aicommerce.agent_service.dto.summary.SessionSummary;
import com.strikerkk.aicommerce.agent_service.dto.summary.ToolCallSummary;
import com.strikerkk.aicommerce.agent_service.entity.AgentAction;
import com.strikerkk.aicommerce.agent_service.entity.AgentMessage;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import com.strikerkk.aicommerce.agent_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Response DTOs")
class ResponseDtoTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private final ModelMapper modelMapper = new ModelMapper();

    @Nested
    @DisplayName("ChatResponse")
    class ChatResponseTest {

        @Test
        @DisplayName("carries everything the UI needs for one turn")
        void carriesEverythingForOneTurn() {
            UUID sessionId = UUID.randomUUID();

            ChatResponse response = ChatResponse.builder()
                    .sessionId(sessionId)
                    .message("Which colour would you like?")
                    .sessionStatus(SessionStatus.CLARIFYING)
                    .clarificationNeeded("COLOR")
                    .quickReplies(List.of("Black", "white"))
                    .actionSummary(ActionSummary.builder().actionType("ADD_TO_CART").build())
                    .toolCallsMade(List.of(ToolCallSummary.builder().toolName("searchProducts").build()))
                    .build();

            assertThat(response.getSessionId()).isEqualTo(sessionId);
            assertThat(response.getMessage()).isEqualTo("Which colour would you like?");
            assertThat(response.getSessionStatus()).isEqualTo(SessionStatus.CLARIFYING);
            assertThat(response.getClarificationNeeded()).isEqualTo("COLOR");
            assertThat(response.getQuickReplies()).containsExactly("Black", "white");
            assertThat(response.getActionSummary().getActionType()).isEqualTo("ADD_TO_CART");
            assertThat(response.getToolCallsMade()).hasSize(1);
        }

        @Test
        @DisplayName("a plain answer leaves the clarification fields empty")
        void aPlainAnswerLeavesClarificationEmpty() {
            ChatResponse response = ChatResponse.builder()
                    .sessionId(UUID.randomUUID())
                    .message("Here you go")
                    .sessionStatus(SessionStatus.ACTIVE)
                    .toolCallsMade(List.of())
                    .build();

            assertThat(response.getClarificationNeeded()).isNull();
            assertThat(response.getQuickReplies()).isNull();
            assertThat(response.getActionSummary()).isNull();
            assertThat(response.getToolCallsMade()).isEmpty();
        }

        @Test
        @DisplayName("serialises the session status as its name")
        void serialisesTheStatusAsItsName() throws Exception {
            String json = objectMapper.writeValueAsString(
                    ChatResponse.builder().sessionStatus(SessionStatus.ACTIVE).build());

            assertThat(json).contains("\"sessionStatus\":\"ACTIVE\"");
        }
    }

    @Nested
    @DisplayName("StartSessionResponse")
    class StartSessionResponseTest {

        @Test
        @DisplayName("works through the builder, the no-arg constructor and the setters")
        void worksBothWays() {
            UUID sessionId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            StartSessionResponse built = StartSessionResponse.builder()
                    .sessionId(sessionId).status(SessionStatus.ACTIVE).message("hi").createdAt(now).build();

            StartSessionResponse set = new StartSessionResponse();
            set.setSessionId(sessionId);
            set.setStatus(SessionStatus.ACTIVE);
            set.setMessage("hi");
            set.setCreatedAt(now);

            assertThat(built).isEqualTo(set);
            assertThat(built).hasSameHashCodeAs(set);
        }

        @Test
        @DisplayName("has an all-args constructor")
        void hasAnAllArgsConstructor() {
            UUID sessionId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();

            StartSessionResponse response =
                    new StartSessionResponse(sessionId, SessionStatus.CLOSED, "bye", now);

            assertThat(response.getSessionId()).isEqualTo(sessionId);
            assertThat(response.getStatus()).isEqualTo(SessionStatus.CLOSED);
            assertThat(response.getMessage()).isEqualTo("bye");
            assertThat(response.getCreatedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("SessionStatusResponse")
    class SessionStatusResponseTest {

        @Test
        @DisplayName("carries the counters and the live Redis fields")
        void carriesCountersAndLiveFields() {
            LocalDateTime now = LocalDateTime.now();

            SessionStatusResponse response = SessionStatusResponse.builder()
                    .sessionId(TestDataFactory.SESSION_ID)
                    .userId(42L)
                    .status(SessionStatus.CLARIFYING)
                    .initialIntent("buy a speaker")
                    .totalMessages(6)
                    .totalActions(2)
                    .pendingClarificationFor("COLOR")
                    .lastAgentMessage("Which colour?")
                    .lastActivityAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            assertThat(response.getTotalMessages()).isEqualTo(6);
            assertThat(response.getTotalActions()).isEqualTo(2);
            assertThat(response.getPendingClarificationFor()).isEqualTo("COLOR");
            assertThat(response.getLastAgentMessage()).isEqualTo("Which colour?");
            assertThat(response.getLastActivityAt()).isEqualTo(now);
            assertThat(response.getUserId()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("Collection wrappers")
    class CollectionWrappers {

        @Test
        @DisplayName("ConversationHistoryResponse keeps the count next to the list")
        void conversationHistory() {
            ConversationHistoryResponse response = ConversationHistoryResponse.builder()
                    .sessionId(TestDataFactory.SESSION_ID)
                    .totalMessages(2)
                    .messages(List.of(new MessageResponse(), new MessageResponse()))
                    .build();

            assertThat(response.getTotalMessages()).isEqualTo(2);
            assertThat(response.getMessages()).hasSize(2);
        }

        @Test
        @DisplayName("SessionActionResponse keeps the count next to the list")
        void sessionActions() {
            SessionActionResponse response = SessionActionResponse.builder()
                    .sessionId(TestDataFactory.SESSION_ID)
                    .totalActions(1)
                    .actions(List.of(new ActionResponse()))
                    .build();

            assertThat(response.getTotalActions()).isEqualTo(1);
            assertThat(response.getActions()).hasSize(1);
        }

        @Test
        @DisplayName("MySessionResponse keeps the count next to the list")
        void mySessions() {
            MySessionResponse response = MySessionResponse.builder()
                    .totalSessions(1)
                    .sessions(List.of(new SessionSummary()))
                    .build();

            assertThat(response.getTotalSessions()).isEqualTo(1);
            assertThat(response.getSessions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Summaries")
    class Summaries {

        @Test
        @DisplayName("ToolCallSummary describes one tool call")
        void toolCallSummary() {
            ToolCallSummary summary = ToolCallSummary.builder()
                    .toolName("addToCart").status("SUCCESS").description("Item added to cart").build();

            assertThat(summary.getToolName()).isEqualTo("addToCart");
            assertThat(summary.getStatus()).isEqualTo("SUCCESS");
            assertThat(summary.getDescription()).isEqualTo("Item added to cart");
            assertThat(summary.toString()).contains("ToolCallSummary");
        }

        @Test
        @DisplayName("ActionSummary describes the outcome of the turn")
        void actionSummary() {
            ActionSummary summary = ActionSummary.builder()
                    .actionType("ORDER_PLACED").resourceId("1001").details("done").build();

            assertThat(summary.getActionType()).isEqualTo("ORDER_PLACED");
            assertThat(summary.getResourceId()).isEqualTo("1001");
            assertThat(summary.getDetails()).isEqualTo("done");
        }

        @Test
        @DisplayName("SessionSummary is a mutable bean, the service fills it in after mapping")
        void sessionSummary() {
            SessionSummary summary = new SessionSummary();

            summary.setSessionId(TestDataFactory.SESSION_ID);
            summary.setStatus(SessionStatus.COMPLETED);
            summary.setOutcome("Order #1001 placed successfully");
            summary.setTotalMessages(4);
            summary.setTotalActions(2);

            assertThat(summary.getOutcome()).isEqualTo("Order #1001 placed successfully");
            assertThat(summary.getTotalMessages()).isEqualTo(4);
            assertThat(summary.getTotalActions()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("ModelMapper contracts")
    class ModelMapperContracts {

        @Test
        @DisplayName("AgentMessage maps onto MessageResponse field for field")
        void agentMessageMapsOntoMessageResponse() {
            AgentSession session = TestDataFactory.session();
            AgentMessage message = TestDataFactory.toolMessage(
                    session, MessageRole.TOOL_RESULT, "addToCart", "{\"productId\":1}", "{\"cartItemId\":9}", 3);

            MessageResponse response = modelMapper.map(message, MessageResponse.class);

            assertThat(response.getMessageId()).isEqualTo(message.getMessageId());
            assertThat(response.getRole()).isEqualTo(MessageRole.TOOL_RESULT);
            assertThat(response.getToolName()).isEqualTo("addToCart");
            assertThat(response.getToolInput()).isEqualTo("{\"productId\":1}");
            assertThat(response.getToolOutput()).isEqualTo("{\"cartItemId\":9}");
            assertThat(response.getSequenceNumber()).isEqualTo(3);
            assertThat(response.getCreatedAt()).isEqualTo(message.getCreatedAt());
        }

        @Test
        @DisplayName("AgentAction maps onto ActionResponse field for field")
        void agentActionMapsOntoActionResponse() {
            AgentAction action = TestDataFactory.action(
                    TestDataFactory.session(), ActionType.PLACE_ORDER, ActionStatus.SUCCESS, "1001");
            action.setFailureReason(null);

            ActionResponse response = modelMapper.map(action, ActionResponse.class);

            assertThat(response.getActionId()).isEqualTo(action.getActionId());
            assertThat(response.getActionType()).isEqualTo(ActionType.PLACE_ORDER);
            assertThat(response.getStatus()).isEqualTo(ActionStatus.SUCCESS);
            assertThat(response.getResourceId()).isEqualTo("1001");
            assertThat(response.getRequestPayload()).isEqualTo(action.getRequestPayload());
            assertThat(response.getResponsePayload()).isEqualTo(action.getResponsePayload());
            assertThat(response.getCreatedAt()).isEqualTo(action.getCreatedAt());
            assertThat(response.getUpdatedAt()).isEqualTo(action.getUpdatedAt());
        }

        @Test
        @DisplayName("AgentSession maps onto SessionSummary without touching the derived fields")
        void agentSessionMapsOntoSessionSummary() {
            AgentSession session = TestDataFactory.session(
                    TestDataFactory.SESSION_ID, 42L, SessionStatus.COMPLETED);

            SessionSummary summary = modelMapper.map(session, SessionSummary.class);

            assertThat(summary.getSessionId()).isEqualTo(TestDataFactory.SESSION_ID);
            assertThat(summary.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(summary.getInitialIntent()).isEqualTo(TestDataFactory.INITIAL_INTENT);
            assertThat(summary.getCreatedAt()).isEqualTo(session.getCreatedAt());
            assertThat(summary.getUpdatedAt()).isEqualTo(session.getUpdatedAt());
            assertThat(summary.getOutcome()).isNull();
        }
    }
}


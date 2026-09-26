package com.strikerkk.aicommerce.agent_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.agent_service.dto.request.ChatRequest;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
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
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import com.strikerkk.aicommerce.agent_service.exception.AgentException;
import com.strikerkk.aicommerce.agent_service.exception.SessionNotFoundException;
import com.strikerkk.aicommerce.agent_service.exception.ToolCallException;
import com.strikerkk.aicommerce.agent_service.exception.UnauthorizedSessionAccessException;
import com.strikerkk.aicommerce.agent_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.agent_service.service.AgentChatService;
import com.strikerkk.aicommerce.agent_service.service.AgentSessionService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentController")
class AgentControllerTest {

    private static final UUID SESSION_ID = TestDataFactory.SESSION_ID;

    @Mock
    private AgentSessionService agentSessionService;

    @Mock
    private AgentChatService agentChatService;

    @InjectMocks
    private AgentController agentController;

    @Captor
    private ArgumentCaptor<ChatRequest> chatCaptor;

    @Captor
    private ArgumentCaptor<StartSessionRequest> startCaptor;

    @Captor
    private ArgumentCaptor<UUID> sessionIdCaptor;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ChatResponse chatResponse() {
        return ChatResponse.builder()
                .sessionId(SESSION_ID)
                .message("Added the JBL speaker to your cart.")
                .sessionStatus(SessionStatus.ACTIVE)
                .toolCallsMade(List.of(ToolCallSummary.builder()
                        .toolName("addToCart").status("SUCCESS").description("Item added to cart").build()))
                .actionSummary(ActionSummary.builder()
                        .actionType("ADD_TO_CART").details("Item added to your cart").build())
                .build();
    }

    private String body(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    // chat ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /agent/chat")
    class Chat {

        @Test
        @DisplayName("returns 200 with the agent's answer")
        void returns200() throws Exception {
            when(agentChatService.chat(any())).thenReturn(chatResponse());

            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.chatRequest(SESSION_ID, "add it to my cart"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Chat processed"))
                    .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID.toString()))
                    .andExpect(jsonPath("$.data.message").value("Added the JBL speaker to your cart."))
                    .andExpect(jsonPath("$.data.sessionStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.toolCallsMade[0].toolName").value("addToCart"))
                    .andExpect(jsonPath("$.data.actionSummary.actionType").value("ADD_TO_CART"));
        }

        @Test
        @DisplayName("hands the body straight to the service")
        void handsTheBodyToTheService() throws Exception {
            when(agentChatService.chat(any())).thenReturn(chatResponse());

            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.chatRequest(SESSION_ID, "add it to my cart"))))
                    .andExpect(status().isOk());

            verify(agentChatService).chat(chatCaptor.capture());

            assertThat(chatCaptor.getValue().getSessionId()).isEqualTo(SESSION_ID);
            assertThat(chatCaptor.getValue().getMessage()).isEqualTo("add it to my cart");
        }

        @Test
        @DisplayName("accepts a first message with no sessionId")
        void acceptsAFirstMessage() throws Exception {
            when(agentChatService.chat(any())).thenReturn(chatResponse());

            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"hi\"}"))
                    .andExpect(status().isOk());

            verify(agentChatService).chat(chatCaptor.capture());
            assertThat(chatCaptor.getValue().getSessionId()).isNull();
        }

        @Test
        @DisplayName("rejects a blank message with 400 and never calls the service")
        void rejectsABlankMessage() throws Exception {
            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("message: Message cannot be blank"));

            verifyNoInteractions(agentChatService);
        }

        @Test
        @DisplayName("rejects a missing message with 400")
        void rejectsAMissingMessage() throws Exception {
            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(agentChatService);
        }

        @Test
        @DisplayName("rejects a message longer than 1000 characters with 400")
        void rejectsATooLongMessage() throws Exception {
            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.chatRequest(null, "a".repeat(1001)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("message: Message cannot exceed 1000 characters"));

            verifyNoInteractions(agentChatService);
        }

        @Test
        @DisplayName("an unknown session becomes 404")
        void anUnknownSessionBecomes404() throws Exception {
            when(agentChatService.chat(any()))
                    .thenThrow(new SessionNotFoundException(SESSION_ID.toString()));

            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.chatRequest(SESSION_ID, "hi"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Session not found: " + SESSION_ID));
        }

        @Test
        @DisplayName("somebody else's session becomes 403")
        void somebodyElsesSessionBecomes403() throws Exception {
            when(agentChatService.chat(any()))
                    .thenThrow(new UnauthorizedSessionAccessException(SESSION_ID.toString()));

            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.chatRequest(SESSION_ID, "hi"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("an LLM outage becomes 500")
        void anLlmOutageBecomes500() throws Exception {
            when(agentChatService.chat(any())).thenThrow(new AgentException("LLM api error: 529"));

            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.chatRequest(SESSION_ID, "hi"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("LLM api error: 529"));
        }

        @Test
        @DisplayName("a downstream outage becomes 502")
        void aDownstreamOutageBecomes502() throws Exception {
            when(agentChatService.chat(any()))
                    .thenThrow(new ToolCallException("addToCart", "cart-service is down"));

            mockMvc.perform(post("/agent/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.chatRequest(SESSION_ID, "hi"))))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.message").value("Service call failed: cart-service is down"));
        }
    }

    @Nested
    @DisplayName("POST /agent/session/start")
    class StartSession {

        @Test
        @DisplayName("returns 201 with the new session")
        void returns201() throws Exception {
            when(agentSessionService.startSession(any())).thenReturn(StartSessionResponse.builder()
                    .sessionId(SESSION_ID)
                    .status(SessionStatus.ACTIVE)
                    .message("Session started. How can I help you today?")
                    .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                    .build());

            mockMvc.perform(post("/agent/session/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.startSessionRequest("buy a speaker"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Session started successfully"))
                    .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID.toString()))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(agentSessionService).startSession(startCaptor.capture());
            assertThat(startCaptor.getValue().getInitialIntent()).isEqualTo("buy a speaker");
        }

        @Test
        @DisplayName("the body is optional - the service receives null")
        void theBodyIsOptional() throws Exception {
            when(agentSessionService.startSession(any())).thenReturn(
                    StartSessionResponse.builder().sessionId(SESSION_ID).status(SessionStatus.ACTIVE).build());

            mockMvc.perform(post("/agent/session/start"))
                    .andExpect(status().isCreated());

            verify(agentSessionService).startSession(null);
        }
    }

    @Nested
    @DisplayName("GET /agent/session/{sessionId}")
    class GetSessionStatus {

        @Test
        @DisplayName("returns 200 with the merged status")
        void returns200() throws Exception {
            when(agentSessionService.getSessionStatus(SESSION_ID)).thenReturn(SessionStatusResponse.builder()
                    .sessionId(SESSION_ID)
                    .userId(TestDataFactory.USER_ID)
                    .status(SessionStatus.CLARIFYING)
                    .totalMessages(6)
                    .totalActions(2)
                    .pendingClarificationFor("COLOR")
                    .lastAgentMessage("Which colour?")
                    .build());

            mockMvc.perform(get("/agent/session/{sessionId}", SESSION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Session fetched successfully"))
                    .andExpect(jsonPath("$.data.status").value("CLARIFYING"))
                    .andExpect(jsonPath("$.data.totalMessages").value(6))
                    .andExpect(jsonPath("$.data.pendingClarificationFor").value("COLOR"))
                    .andExpect(jsonPath("$.data.lastAgentMessage").value("Which colour?"));

            verify(agentSessionService).getSessionStatus(sessionIdCaptor.capture());
            assertThat(sessionIdCaptor.getValue()).isEqualTo(SESSION_ID);
        }

        @Test
        @DisplayName("a malformed UUID never reaches the service")
        void aMalformedUuidNeverReachesTheService() throws Exception {
            mockMvc.perform(get("/agent/session/{sessionId}", "not-a-uuid"))
                    .andExpect(status().is5xxServerError());

            verify(agentSessionService, never()).getSessionStatus(any());
        }

        @Test
        @DisplayName("an unknown session becomes 404")
        void anUnknownSessionBecomes404() throws Exception {
            when(agentSessionService.getSessionStatus(SESSION_ID))
                    .thenThrow(new SessionNotFoundException(SESSION_ID.toString()));

            mockMvc.perform(get("/agent/session/{sessionId}", SESSION_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /agent/session/{sessionId}/end")
    class EndSession {

        @Test
        @DisplayName("returns 200 with no payload")
        void returns200() throws Exception {
            doNothing().when(agentSessionService).endSession(SESSION_ID);

            mockMvc.perform(delete("/agent/session/{sessionId}/end", SESSION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Session ended successfully"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(agentSessionService).endSession(SESSION_ID);
        }

        @Test
        @DisplayName("somebody else's session becomes 403")
        void somebodyElsesSessionBecomes403() throws Exception {
            doThrow(new UnauthorizedSessionAccessException(SESSION_ID.toString()))
                    .when(agentSessionService).endSession(SESSION_ID);

            mockMvc.perform(delete("/agent/session/{sessionId}/end", SESSION_ID))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /agent/session/{sessionId}/history")
    class GetConversationHistory {

        @Test
        @DisplayName("returns 200 with the replayed conversation")
        void returns200() throws Exception {
            MessageResponse first = new MessageResponse();
            first.setRole(MessageRole.USER);
            first.setContent("I want a speaker");
            first.setSequenceNumber(0);

            MessageResponse second = new MessageResponse();
            second.setRole(MessageRole.ASSISTANT);
            second.setContent("I found three");
            second.setSequenceNumber(1);

            when(agentSessionService.getConversationHistory(SESSION_ID))
                    .thenReturn(ConversationHistoryResponse.builder()
                            .sessionId(SESSION_ID)
                            .totalMessages(2)
                            .messages(List.of(first, second))
                            .build());

            mockMvc.perform(get("/agent/session/{sessionId}/history", SESSION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Session history fetched successfully"))
                    .andExpect(jsonPath("$.data.totalMessages").value(2))
                    .andExpect(jsonPath("$.data.messages[0].role").value("USER"))
                    .andExpect(jsonPath("$.data.messages[1].content").value("I found three"));
        }

        @Test
        @DisplayName("an empty history surfaces as a safe 500")
        void anEmptyHistorySurfacesAsA500() throws Exception {
            when(agentSessionService.getConversationHistory(SESSION_ID))
                    .thenThrow(new RuntimeException("No conversation history found for sessionId: " + SESSION_ID));

            mockMvc.perform(get("/agent/session/{sessionId}/history", SESSION_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Something went wrong, Please try again."));
        }
    }

    @Nested
    @DisplayName("GET /agent/session/my")
    class GetMySessions {

        @Test
        @DisplayName("returns 200 with every session of the caller")
        void returns200() throws Exception {
            SessionSummary summary = new SessionSummary();
            summary.setSessionId(SESSION_ID);
            summary.setStatus(SessionStatus.COMPLETED);
            summary.setOutcome("Order #1001 placed successfully");
            summary.setTotalMessages(6);
            summary.setTotalActions(3);

            when(agentSessionService.getMySessions()).thenReturn(MySessionResponse.builder()
                    .totalSessions(1)
                    .sessions(List.of(summary))
                    .build());

            mockMvc.perform(get("/agent/session/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("My all sessions fetched successfully"))
                    .andExpect(jsonPath("$.data.totalSessions").value(1))
                    .andExpect(jsonPath("$.data.sessions[0].outcome")
                            .value("Order #1001 placed successfully"));
        }

        @Test
        @DisplayName("/session/my is not mistaken for a sessionId")
        void myIsNotMistakenForASessionId() throws Exception {
            when(agentSessionService.getMySessions()).thenReturn(
                    MySessionResponse.builder().totalSessions(0).sessions(List.of()).build());

            mockMvc.perform(get("/agent/session/my"))
                    .andExpect(status().isOk());

            verify(agentSessionService, never()).getSessionStatus(any());
        }
    }

    @Nested
    @DisplayName("GET /agent/session/{sessionId}/actions")
    class GetSessionActions {

        @Test
        @DisplayName("returns 200 with the audit trail")
        void returns200() throws Exception {
            ActionResponse action = new ActionResponse();
            action.setActionId(TestDataFactory.ACTION_ID);
            action.setActionType(ActionType.PLACE_ORDER);
            action.setStatus(ActionStatus.SUCCESS);
            action.setResourceId("1001");

            when(agentSessionService.getSessionActions(SESSION_ID)).thenReturn(SessionActionResponse.builder()
                    .sessionId(SESSION_ID)
                    .totalActions(1)
                    .actions(List.of(action))
                    .build());

            mockMvc.perform(get("/agent/session/{sessionId}/actions", SESSION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Session actions fetched successfully"))
                    .andExpect(jsonPath("$.data.totalActions").value(1))
                    .andExpect(jsonPath("$.data.actions[0].actionType").value("PLACE_ORDER"))
                    .andExpect(jsonPath("$.data.actions[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.actions[0].resourceId").value("1001"));
        }

        @Test
        @DisplayName("an empty audit trail is still a 200")
        void anEmptyAuditTrailIsStillA200() throws Exception {
            when(agentSessionService.getSessionActions(SESSION_ID)).thenReturn(SessionActionResponse.builder()
                    .sessionId(SESSION_ID).totalActions(0).actions(List.of()).build());

            mockMvc.perform(get("/agent/session/{sessionId}/actions", SESSION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalActions").value(0));
        }
    }
}


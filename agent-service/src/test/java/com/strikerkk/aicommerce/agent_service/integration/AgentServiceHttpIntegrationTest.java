package com.strikerkk.aicommerce.agent_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.agent_service.auth.UserContext;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import com.strikerkk.aicommerce.agent_service.llm.ToolExecutionService;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.repository.AgentActionRepository;
import com.strikerkk.aicommerce.agent_service.repository.AgentMessageRepository;
import com.strikerkk.aicommerce.agent_service.repository.AgentSessionRepository;
import com.strikerkk.aicommerce.agent_service.support.InMemorySessionContextRedis;
import com.strikerkk.aicommerce.agent_service.support.StubLlmServer;
import com.strikerkk.aicommerce.agent_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Agent service HTTP wiring")
class AgentServiceHttpIntegrationTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_EMAIL_HEADER = "X-user-email";
    private static final String USER_ID = "42";
    private static final String OTHER_USER_ID = "99";
    private static final String USER_EMAIL = "striker@example.com";

    private static final StubLlmServer LLM = StubLlmServer.start();

    @DynamicPropertySource
    static void pointTheAgentAtTheStub(DynamicPropertyRegistry registry) {
        registry.add("anthropic.url", LLM::url);
        registry.add("anthropic.api.key", () -> "test-api-key");
        registry.add("anthropic.api.model", () -> "claude-sonnet-4-5");
        registry.add("anthropic.api.max-tokens", () -> 1000);
    }

    @AfterAll
    static void stopTheStub() {
        LLM.close();
    }

    @TestConfiguration
    static class RedisStore {

        static final Map<String, SessionContext> STORE = InMemorySessionContextRedis.newStore();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentSessionRepository agentSessionRepository;

    @Autowired
    private AgentMessageRepository agentMessageRepository;

    @Autowired
    private AgentActionRepository agentActionRepository;

    @MockitoBean
    private ToolExecutionService toolExecutionService;

    /** Replaces the real template, so no Redis server is needed to run the suite. */
    @MockitoBean(name = "sessionContextRedisTemplate")
    private RedisTemplate<String, SessionContext> sessionContextRedisTemplate;

    @BeforeEach
    void setUp() {
        LLM.reset();
        RedisStore.STORE.clear();

        // bean-override mocks are reset between tests, so teach it the map behaviour again
        InMemorySessionContextRedis.applyTo(sessionContextRedisTemplate, RedisStore.STORE);

        agentActionRepository.deleteAll();
        agentMessageRepository.deleteAll();
        agentSessionRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        agentActionRepository.deleteAll();
        agentMessageRepository.deleteAll();
        agentSessionRepository.deleteAll();
    }

    private UUID startSession(String userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/agent/session/start")
                        .header(USER_ID_HEADER, userId)
                        .header(USER_EMAIL_HEADER, USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialIntent\":\"buy a speaker\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("sessionId").asText());
    }

    private String chatBody(UUID sessionId, String message) throws Exception {
        return objectMapper.writeValueAsString(
                sessionId == null ? Map.of("message", message)
                        : Map.of("sessionId", sessionId.toString(), "message", message));
    }

    // session start ---------------------------------------------------------------------------

    @Nested
    @DisplayName("Session lifecycle")
    class SessionLifecycle {

        @Test
        @DisplayName("starting a session writes a row and a Redis context")
        void startingASessionWritesEverything() throws Exception {
            UUID sessionId = startSession(USER_ID);

            assertThat(agentSessionRepository.findById(sessionId)).get().satisfies(session -> {
                assertThat(session.getUserId()).isEqualTo(42L);
                assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
                assertThat(session.getInitialIntent()).isEqualTo("buy a speaker");
            });

            assertThat(RedisStore.STORE)
                    .containsKey("session:" + sessionId)
                    .containsKey("user:session:42");
        }

        @Test
        @DisplayName("starting twice reuses the live session")
        void startingTwiceReusesTheLiveSession() throws Exception {
            UUID first = startSession(USER_ID);

            mockMvc.perform(post("/agent/session/start")
                            .header(USER_ID_HEADER, USER_ID)
                            .header(USER_EMAIL_HEADER, USER_EMAIL))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.sessionId").value(first.toString()))
                    .andExpect(jsonPath("$.data.message")
                            .value("You already have an active session. Continuing from where you left off."));

            assertThat(agentSessionRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("the status endpoint reports the live counters")
        void theStatusEndpointReportsTheCounters() throws Exception {
            UUID sessionId = startSession(USER_ID);
            LLM.willReturn(TestDataFactory.llmEndTurn("Here are three speakers."));

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "show me speakers")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/agent/session/{sessionId}", sessionId).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.totalMessages").value(2))
                    .andExpect(jsonPath("$.data.totalActions").value(0))
                    .andExpect(jsonPath("$.data.lastAgentMessage").value("Here are three speakers."));
        }

        @Test
        @DisplayName("ending a session closes the row and drops the Redis keys")
        void endingASessionClosesEverything() throws Exception {
            UUID sessionId = startSession(USER_ID);

            mockMvc.perform(delete("/agent/session/{sessionId}/end", sessionId).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Session ended successfully"));

            assertThat(agentSessionRepository.findById(sessionId))
                    .get().extracting(AgentSession::getStatus).isEqualTo(SessionStatus.CLOSED);
            assertThat(RedisStore.STORE).isEmpty();
        }

        @Test
        @DisplayName("a session belonging to somebody else is a 403 on every endpoint")
        void anotherUsersSessionIsAlways403() throws Exception {
            UUID sessionId = startSession(USER_ID);

            mockMvc.perform(get("/agent/session/{sessionId}", sessionId).header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/agent/session/{sessionId}/history", sessionId)
                            .header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/agent/session/{sessionId}/actions", sessionId)
                            .header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/agent/session/{sessionId}/end", sessionId)
                            .header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("an unknown session is a 404")
        void anUnknownSessionIsA404() throws Exception {
            mockMvc.perform(get("/agent/session/{sessionId}", UUID.randomUUID())
                            .header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Chat")
    class Chat {

        @Test
        @DisplayName("a plain question is answered and both turns are persisted")
        void aPlainQuestionIsAnswered() throws Exception {
            UUID sessionId = startSession(USER_ID);
            LLM.willReturn(TestDataFactory.llmEndTurn("Here are three speakers."));

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "show me speakers")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.message").value("Here are three speakers."))
                    .andExpect(jsonPath("$.data.sessionStatus").value("ACTIVE"));

            assertThat(agentMessageRepository.countBySession_SessionId(sessionId)).isEqualTo(2);
            assertThat(agentMessageRepository.findBySession_SessionIdOrderBySequenceNumberAsc(sessionId))
                    .extracting("role")
                    .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        }

        @Test
        @DisplayName("the first message without a sessionId opens a session on the fly")
        void theFirstMessageOpensASession() throws Exception {
            LLM.willReturn(TestDataFactory.llmEndTurn("Hello! How can I help?"));

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .header(USER_EMAIL_HEADER, USER_EMAIL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(null, "hi")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                    .andExpect(jsonPath("$.data.message").value("Hello! How can I help?"));

            assertThat(agentSessionRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("a tool call is executed, audited and fed back to the model")
        void aToolCallIsExecutedAndAudited() throws Exception {
            UUID sessionId = startSession(USER_ID);

            LLM.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "addToCart",
                            "{\"productId\":7,\"variantId\":9,\"quantity\":1}"),
                    TestDataFactory.llmEndTurn("Added the JBL speaker to your cart."));

            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn("{\"cartItemId\":9}");

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "add it to my cart")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.message").value("Added the JBL speaker to your cart."))
                    .andExpect(jsonPath("$.data.toolCallsMade[0].toolName").value("addToCart"))
                    .andExpect(jsonPath("$.data.toolCallsMade[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.actionSummary.actionType").value("ADD_TO_CART"));

            assertThat(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(sessionId))
                    .singleElement()
                    .satisfies(action -> {
                        assertThat(action.getActionType()).isEqualTo(ActionType.ADD_TO_CART);
                        assertThat(action.getStatus()).isEqualTo(ActionStatus.SUCCESS);
                        assertThat(action.getResourceId()).isEqualTo("9");
                        assertThat(action.getUserId()).isEqualTo(42L);
                    });

            // user + tool call + tool result + assistant
            assertThat(agentMessageRepository.countBySession_SessionId(sessionId)).isEqualTo(4);
        }

        @Test
        @DisplayName("a downstream outage is audited as a FAILURE without breaking the turn")
        void aDownstreamOutageIsAuditedAsAFailure() throws Exception {
            UUID sessionId = startSession(USER_ID);

            LLM.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "placeOrder", "{\"addressId\":5}"),
                    TestDataFactory.llmEndTurn("I could not place the order just now."));

            when(toolExecutionService.executeTool(eq("placeOrder"), any()))
                    .thenReturn(TestDataFactory.toolFailure("placeOrder", "order-service is down"));

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "place my order")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.toolCallsMade[0].status").value("FAILED"))
                    .andExpect(jsonPath("$.data.actionSummary").doesNotExist());

            assertThat(agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(sessionId))
                    .singleElement()
                    .satisfies(action -> {
                        assertThat(action.getStatus()).isEqualTo(ActionStatus.FAILURE);
                        assertThat(action.getFailureReason()).contains("order-service is down");
                    });
        }

        @Test
        @DisplayName("a clarifying question flips the session to CLARIFYING and offers quick replies")
        void aClarifyingQuestionFlipsTheSession() throws Exception {
            UUID sessionId = startSession(USER_ID);
            LLM.willReturn(TestDataFactory.llmPauseTurn("Which colour would you like?"));

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "I want a speaker")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionStatus").value("CLARIFYING"))
                    .andExpect(jsonPath("$.data.clarificationNeeded").value("COLOR"))
                    .andExpect(jsonPath("$.data.quickReplies[0]").value("Black"));

            assertThat(agentSessionRepository.findById(sessionId))
                    .get().extracting(AgentSession::getStatus).isEqualTo(SessionStatus.CLARIFYING);
        }

        @Test
        @DisplayName("answering the clarification moves the session back to ACTIVE")
        void answeringTheClarificationRestoresActive() throws Exception {
            UUID sessionId = startSession(USER_ID);

            LLM.willReturn(TestDataFactory.llmPauseTurn("Which colour would you like?"));
            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "I want a speaker")))
                    .andExpect(status().isOk());

            LLM.willReturn(TestDataFactory.llmEndTurn("Great - the black one it is."));
            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "black")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.clarificationNeeded").doesNotExist());

            assertThat(agentSessionRepository.findById(sessionId))
                    .get().extracting(AgentSession::getStatus).isEqualTo(SessionStatus.ACTIVE);
        }

        @Test
        @DisplayName("a blank message is rejected before anything is touched")
        void aBlankMessageIsRejected() throws Exception {
            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            assertThat(agentSessionRepository.count()).isZero();
            assertThat(LLM.callCount()).isZero();
        }

        @Test
        @DisplayName("an LLM outage becomes a 500 the caller can understand")
        void anLlmOutageBecomesA500() throws Exception {
            UUID sessionId = startSession(USER_ID);
            LLM.withStatus(529).willAlwaysReturn("{\"error\":\"overloaded\"}");

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "hi")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("LLM api error: 529"));
        }

        @Test
        @DisplayName("chatting into somebody else's session is a 403")
        void chattingIntoAnotherUsersSessionIs403() throws Exception {
            UUID sessionId = startSession(USER_ID);

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, OTHER_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "hi")))
                    .andExpect(status().isForbidden());

            assertThat(LLM.callCount()).isZero();
        }
    }

    @Nested
    @DisplayName("History and audit")
    class HistoryAndAudit {

        @Test
        @DisplayName("the history endpoint replays every turn, tools included")
        void theHistoryEndpointReplaysEveryTurn() throws Exception {
            UUID sessionId = startSession(USER_ID);

            LLM.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "searchProducts", "{\"search\":\"speaker\"}"),
                    TestDataFactory.llmEndTurn("I found three."));
            when(toolExecutionService.executeTool(eq("searchProducts"), any())).thenReturn("[{\"id\":7}]");

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "show me speakers")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/agent/session/{sessionId}/history", sessionId).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalMessages").value(4))
                    .andExpect(jsonPath("$.data.messages[0].content").value("show me speakers"))
                    .andExpect(jsonPath("$.data.messages[1].toolName").value("searchProducts"))
                    .andExpect(jsonPath("$.data.messages[2].toolOutput").value("[{\"id\":7}]"))
                    .andExpect(jsonPath("$.data.messages[3].content").value("I found three."));
        }

        @Test
        @DisplayName("the actions endpoint exposes the audit trail")
        void theActionsEndpointExposesTheAuditTrail() throws Exception {
            UUID sessionId = startSession(USER_ID);

            LLM.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "placeOrder", "{\"addressId\":5}"),
                    TestDataFactory.llmEndTurn("Order placed."));
            when(toolExecutionService.executeTool(eq("placeOrder"), any())).thenReturn("{\"orderId\":1001}");

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "place my order")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/agent/session/{sessionId}/actions", sessionId).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalActions").value(1))
                    .andExpect(jsonPath("$.data.actions[0].actionType").value("PLACE_ORDER"))
                    .andExpect(jsonPath("$.data.actions[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.actions[0].resourceId").value("1001"));
        }

        @Test
        @DisplayName("my-sessions summarises the outcome of each session")
        void mySessionsSummarisesTheOutcome() throws Exception {
            UUID sessionId = startSession(USER_ID);

            LLM.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "placeOrder", "{\"addressId\":5}"),
                    TestDataFactory.llmEndTurn("Order placed."));
            when(toolExecutionService.executeTool(eq("placeOrder"), any())).thenReturn("{\"orderId\":1001}");

            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "place my order")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/agent/session/my").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalSessions").value(1))
                    .andExpect(jsonPath("$.data.sessions[0].outcome")
                            .value("Order #1001 placed successfully"))
                    .andExpect(jsonPath("$.data.sessions[0].totalActions").value(1));
        }

        @Test
        @DisplayName("my-sessions never leaks another user's sessions")
        void mySessionsNeverLeaks() throws Exception {
            startSession(USER_ID);

            mockMvc.perform(get("/agent/session/my").header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalSessions").value(0));
        }
    }

    @Nested
    @DisplayName("Redis context")
    class RedisContext {

        @Test
        @DisplayName("the conversation survives in Redis between two turns")
        void theConversationSurvivesBetweenTurns() throws Exception {
            UUID sessionId = startSession(USER_ID);

            LLM.willReturn(TestDataFactory.llmEndTurn("Here are three."));
            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "show me speakers")))
                    .andExpect(status().isOk());

            LLM.willReturn(TestDataFactory.llmEndTurn("The JBL one is 8999."));
            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "how much is the first one?")))
                    .andExpect(status().isOk());

            SessionContext context = RedisStore.STORE.get("session:" + sessionId);

            assertThat(context).isNotNull();
            assertThat(context.getConversationMessages()).hasSize(4);
            assertThat(context.getUserId()).isEqualTo(42L);

            // the second call to the model replayed the whole conversation
            assertThat(objectMapper.readTree(LLM.lastRequestBody()).path("messages")).hasSize(3);
        }

        @Test
        @DisplayName("an expired Redis context is rebuilt from Postgres and the turn still works")
        void anExpiredContextIsRebuiltFromPostgres() throws Exception {
            UUID sessionId = startSession(USER_ID);

            LLM.willReturn(TestDataFactory.llmEndTurn("Here are three."));
            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "show me speakers")))
                    .andExpect(status().isOk());

            // simulate the TTL running out
            RedisStore.STORE.clear();

            LLM.willReturn(TestDataFactory.llmEndTurn("Welcome back."));
            mockMvc.perform(post("/agent/chat")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(chatBody(sessionId, "and the black one?")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.message").value("Welcome back."));

            SessionContext rebuilt = RedisStore.STORE.get("session:" + sessionId);

            assertThat(rebuilt).isNotNull();
            assertThat(rebuilt.getConversationMessages())
                    .extracting("content")
                    .containsExactly("show me speakers", "Here are three.", "and the black one?", "Welcome back.");
        }
    }
}



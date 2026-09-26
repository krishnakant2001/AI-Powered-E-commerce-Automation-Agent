package com.strikerkk.aicommerce.agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.agent_service.auth.UserContext;
import com.strikerkk.aicommerce.agent_service.dto.request.ChatRequest;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import com.strikerkk.aicommerce.agent_service.dto.response.ChatResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.ConversationHistoryResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.MessageResponse;
import com.strikerkk.aicommerce.agent_service.dto.summary.ToolCallSummary;
import com.strikerkk.aicommerce.agent_service.entity.AgentAction;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import com.strikerkk.aicommerce.agent_service.exception.AgentException;
import com.strikerkk.aicommerce.agent_service.exception.SessionNotFoundException;
import com.strikerkk.aicommerce.agent_service.exception.UnauthorizedSessionAccessException;
import com.strikerkk.aicommerce.agent_service.llm.SystemPromptBuilder;
import com.strikerkk.aicommerce.agent_service.llm.ToolDefinitionBuilder;
import com.strikerkk.aicommerce.agent_service.llm.ToolExecutionService;
import com.strikerkk.aicommerce.agent_service.model.ConversationMessage;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.repository.AgentSessionRepository;
import com.strikerkk.aicommerce.agent_service.support.StubLlmServer;
import com.strikerkk.aicommerce.agent_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentChatService")
class AgentChatServiceTest {

    private static final UUID SESSION_ID = TestDataFactory.SESSION_ID;
    private static final Long USER_ID = TestDataFactory.USER_ID;
    private static final Long OTHER_USER_ID = TestDataFactory.OTHER_USER_ID;

    private static StubLlmServer llm;

    @Mock
    private AgentSessionRepository agentSessionRepository;

    @Mock
    private SessionContextService sessionContextService;

    @Mock
    private AgentSessionService agentSessionService;

    @Mock
    private SystemPromptBuilder systemPrompt;

    @Mock
    private ToolDefinitionBuilder toolDefinition;

    @Mock
    private ToolExecutionService toolExecutionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AgentChatService agentChatService;

    @Captor
    private ArgumentCaptor<SessionContext> contextCaptor;

    @Captor
    private ArgumentCaptor<JsonNode> toolInputCaptor;

    private AgentSession session;
    private SessionContext context;

    @BeforeAll
    static void startLlm() {
        llm = StubLlmServer.start();
    }

    @AfterAll
    static void stopLlm() {
        llm.close();
    }

    @BeforeEach
    void setUp() {
        llm.reset();

        UserContext.setUserId(String.valueOf(USER_ID));
        UserContext.setUserEmail(TestDataFactory.USER_EMAIL);

        ReflectionTestUtils.setField(agentChatService, "llmKey", "test-api-key");
        ReflectionTestUtils.setField(agentChatService, "model", "claude-sonnet-4-5");
        ReflectionTestUtils.setField(agentChatService, "maxTokens", 1000);
        ReflectionTestUtils.setField(agentChatService, "LLL_URL", llm.url());

        when(systemPrompt.build()).thenReturn("you are a shopping assistant");
        when(toolDefinition.build()).thenReturn(List.of());

        session = TestDataFactory.session(SESSION_ID, USER_ID, SessionStatus.ACTIVE);
        context = TestDataFactory.context();

        when(agentSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.of(context));

        when(agentSessionService.saveAction(any(), anyLong(), any(), anyString()))
                .thenReturn(TestDataFactory.pendingAction());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        UserContext.setUserEmail(null);
    }

    private ChatRequest request(String message) {
        return TestDataFactory.chatRequest(SESSION_ID, message);
    }

    // llm modal text ---------------------------------------------------------------------------

    @Nested
    @DisplayName("A plain answer")
    class PlainAnswer {

        @Test
        @DisplayName("returns the model's text and closes the turn after a single call")
        void returnsTheModelsText() {
            llm.willReturn(TestDataFactory.llmEndTurn("Here are three speakers I found."));

            ChatResponse response = agentChatService.chat(request("show me speakers"));

            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(response.getMessage()).isEqualTo("Here are three speakers I found.");
            assertThat(response.getSessionStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(response.getClarificationNeeded()).isNull();
            assertThat(response.getQuickReplies()).isNull();
            assertThat(response.getToolCallsMade()).isEmpty();
            assertThat(response.getActionSummary()).isNull();
            assertThat(llm.callCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("stores the user turn and the assistant turn in Postgres")
        void storesBothTurnsInPostgres() {
            llm.willReturn(TestDataFactory.llmEndTurn("Here you go"));

            agentChatService.chat(request("show me speakers"));

            verify(agentSessionService).saveMessage(
                    session, MessageRole.USER, "show me speakers", null, null, null);
            verify(agentSessionService).saveMessage(
                    session, MessageRole.ASSISTANT, "Here you go", null, null, null);
        }

        @Test
        @DisplayName("appends both turns to the Redis context and saves it")
        void appendsBothTurnsToRedis() {
            llm.willReturn(TestDataFactory.llmEndTurn("Here you go"));

            agentChatService.chat(request("show me speakers"));

            verify(sessionContextService).save(contextCaptor.capture());

            assertThat(contextCaptor.getValue().getConversationMessages())
                    .extracting(ConversationMessage::getRole, ConversationMessage::getContent)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("user", "show me speakers"),
                            org.assertj.core.groups.Tuple.tuple("assistant", "Here you go"));
        }

        @Test
        @DisplayName("sends the whole conversation, the system prompt and the tools to the model")
        void sendsEverythingToTheModel() throws Exception {
            context.getConversationMessages().add(TestDataFactory.userTurn("hi"));
            context.getConversationMessages().add(TestDataFactory.assistantTurn("hello"));
            llm.willReturn(TestDataFactory.llmEndTurn("ok"));

            agentChatService.chat(request("show me speakers"));

            JsonNode body = new ObjectMapper().readTree(llm.lastRequestBody());

            assertThat(body.path("model").asText()).isEqualTo("claude-sonnet-4-5");
            assertThat(body.path("max_tokens").asInt()).isEqualTo(1000);
            assertThat(body.path("system").asText()).isEqualTo("you are a shopping assistant");
            assertThat(body.path("tools").isArray()).isTrue();
            assertThat(body.path("messages")).hasSize(3);
            assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
            assertThat(body.path("messages").get(2).path("content").asText()).isEqualTo("show me speakers");
        }

        @Test
        @DisplayName("authenticates with the Anthropic headers")
        void authenticatesWithTheAnthropicHeaders() {
            llm.willReturn(TestDataFactory.llmEndTurn("ok"));

            agentChatService.chat(request("hi"));

            assertThat(llm.header("x-api-key")).isEqualTo("test-api-key");
            assertThat(llm.header("anthropic-version")).isEqualTo("2023-06-01");
            assertThat(llm.header("content-type")).isEqualTo("application/json");
        }

        @Test
        @DisplayName("falls back to a polite nudge when the model returns no text block")
        void fallsBackWhenThereIsNoTextBlock() {
            llm.willReturn("{\"stop_reason\":\"end_turn\",\"content\":[]}");

            assertThat(agentChatService.chat(request("hi")).getMessage())
                    .isEqualTo("I'm not sure how to respond to that. Could you rephrase?");
        }

        @Test
        @DisplayName("a null content block never reaches the model as a null")
        void nullContentIsSentAsAnEmptyString() throws Exception {
            context.getConversationMessages().add(
                    ConversationMessage.builder().role("assistant").content(null).build());
            llm.willReturn(TestDataFactory.llmEndTurn("ok"));

            agentChatService.chat(request("hi"));

            JsonNode body = new ObjectMapper().readTree(llm.lastRequestBody());

            assertThat(body.path("messages").get(0).path("content").asText()).isEmpty();
        }
    }

    @Nested
    @DisplayName("A tool call")
    class ToolCall {

        @BeforeEach
        void modelAsksForAddToCart() {
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "addToCart",
                            "{\"productId\":7,\"variantId\":9,\"quantity\":1}"),
                    TestDataFactory.llmEndTurn("Added the JBL speaker to your cart."));
        }

        @Test
        @DisplayName("runs the tool and loops back to the model")
        void runsTheToolAndLoopsBack() {
            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn("{\"cartItemId\":9}");

            ChatResponse response = agentChatService.chat(request("add it to my cart"));

            assertThat(llm.callCount()).isEqualTo(2);
            assertThat(response.getMessage()).isEqualTo("Added the JBL speaker to your cart.");

            verify(toolExecutionService).executeTool(eq("addToCart"), toolInputCaptor.capture());
            assertThat(toolInputCaptor.getValue().path("productId").asLong()).isEqualTo(7L);
            assertThat(toolInputCaptor.getValue().path("quantity").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("logs the action as PENDING first and updates it with the result")
        void logsThenUpdatesTheAction() {
            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn("{\"cartItemId\":9}");

            agentChatService.chat(request("add it to my cart"));

            verify(agentSessionService).saveAction(session, USER_ID, ActionType.ADD_TO_CART,
                    "{\"productId\":7,\"variantId\":9,\"quantity\":1}");

            verify(agentSessionService).updateActionResult(
                    TestDataFactory.ACTION_ID, ActionStatus.SUCCESS, "{\"cartItemId\":9}", "9", null);
        }

        @Test
        @DisplayName("records the tool call and the tool result in Postgres")
        void recordsBothHalvesInPostgres() {
            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn("{\"cartItemId\":9}");

            agentChatService.chat(request("add it to my cart"));

            verify(agentSessionService).saveMessage(session, MessageRole.USER, null,
                    "addToCart", "{\"productId\":7,\"variantId\":9,\"quantity\":1}", null);
            verify(agentSessionService).saveMessage(session, MessageRole.TOOL_RESULT, null,
                    "addToCart", null, "{\"cartItemId\":9}");
        }

        @Test
        @DisplayName("summarises the tool call for the UI")
        void summarisesTheToolCall() {
            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn("{\"cartItemId\":9}");

            ChatResponse response = agentChatService.chat(request("add it to my cart"));

            assertThat(response.getToolCallsMade()).singleElement().satisfies(summary -> {
                assertThat(summary.getToolName()).isEqualTo("addToCart");
                assertThat(summary.getStatus()).isEqualTo("SUCCESS");
                assertThat(summary.getDescription()).isEqualTo("Item added to cart");
            });

            assertThat(response.getActionSummary().getActionType()).isEqualTo("ADD_TO_CART");
            assertThat(response.getActionSummary().getDetails()).isEqualTo("Item added to your cart");
        }

        @Test
        @DisplayName("feeds the tool result back into the conversation the model sees")
        void feedsTheResultBackIntoTheConversation() {
            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn("{\"cartItemId\":9}");

            agentChatService.chat(request("add it to my cart"));

            verify(sessionContextService).save(contextCaptor.capture());
            List<ConversationMessage> conversation = contextCaptor.getValue().getConversationMessages();

            assertThat(conversation).hasSize(4);
            assertThat(conversation.get(1).getContent()).contains("\"type\":\"tool_use\"", "toolu_1", "addToCart");
            assertThat(conversation.get(2).getToolName()).isEqualTo("tool_result");
            assertThat(conversation.get(2).getContent()).contains("tool_result", "toolu_1");
            assertThat(conversation.get(3).getRole()).isEqualTo("assistant");
        }

        @Test
        @DisplayName("a tool result marked with the error flag is stored as a FAILURE")
        void aFailedToolIsRecordedAsAFailure() {
            String failure = TestDataFactory.toolFailure("addToCart", "cart-service is down");
            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn(failure);

            ChatResponse response = agentChatService.chat(request("add it to my cart"));

            verify(agentSessionService).updateActionResult(
                    TestDataFactory.ACTION_ID, ActionStatus.FAILURE, failure, null, failure);

            assertThat(response.getToolCallsMade()).singleElement().satisfies(summary -> {
                assertThat(summary.getStatus()).isEqualTo("FAILED");
                assertThat(summary.getDescription()).isEqualTo("Failed: addToCart");
            });
            assertThat(response.getActionSummary()).isNull();
        }

        @Test
        @DisplayName("a failed tool still lets the conversation continue")
        void aFailedToolStillContinues() {
            when(toolExecutionService.executeTool(eq("addToCart"), any()))
                    .thenReturn(TestDataFactory.toolFailure("addToCart", "cart-service is down"));

            assertThat(agentChatService.chat(request("add it to my cart")).getMessage())
                    .isEqualTo("Added the JBL speaker to your cart.");
        }
    }

    @Nested
    @DisplayName("Tool name mapping")
    class ToolNameMapping {

        private void modelCalls(String toolName, String input, String result) {
            llm.reset();
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", toolName, input),
                    TestDataFactory.llmEndTurn("done"));
            when(toolExecutionService.executeTool(eq(toolName), any())).thenReturn(result);
        }

        @Test
        @DisplayName("every known tool maps onto its own action type")
        void everyKnownToolMapsOntoItsActionType() {
            record Case(String tool, ActionType type) {
            }

            List<Case> cases = List.of(
                    new Case("searchProducts", ActionType.SEARCH_PRODUCT),
                    new Case("getProductDetails", ActionType.GET_PRODUCT_DETAILS),
                    new Case("getVariantInfo", ActionType.GET_VARIANT_INFO),
                    new Case("addToCart", ActionType.ADD_TO_CART),
                    new Case("clearCart", ActionType.CLEAR_CART),
                    new Case("placeOrder", ActionType.PLACE_ORDER),
                    new Case("buyNow", ActionType.BUY_NOW),
                    new Case("getOrder", ActionType.GET_ORDER_STATUS),
                    new Case("getMyOrders", ActionType.GET_ORDER_STATUS),
                    new Case("initiatePayment", ActionType.INITIATE_PAYMENT),
                    new Case("getAllAddresses", ActionType.GET_USER_ADDRESS));

            for (Case testCase : cases) {
                modelCalls(testCase.tool(), "{}", "{}");
                context.getConversationMessages().clear();

                agentChatService.chat(request("do it"));

                verify(agentSessionService).saveAction(
                        eq(session), eq(USER_ID), eq(testCase.type()), anyString());
                org.mockito.Mockito.clearInvocations(agentSessionService);
                when(agentSessionService.saveAction(any(), anyLong(), any(), anyString()))
                        .thenReturn(TestDataFactory.pendingAction());
            }
        }

        @Test
        @DisplayName("an unrecognised tool still gets logged, as a product search")
        void anUnrecognisedToolStillGetsLogged() {
            modelCalls("somethingBrandNew", "{}", "{}");

            agentChatService.chat(request("do it"));

            verify(agentSessionService).saveAction(
                    eq(session), eq(USER_ID), eq(ActionType.SEARCH_PRODUCT), anyString());
        }

        @Test
        @DisplayName("an unrecognised tool gets a generic description")
        void anUnrecognisedToolGetsAGenericDescription() {
            modelCalls("somethingBrandNew", "{}", "{}");

            assertThat(agentChatService.chat(request("do it")).getToolCallsMade())
                    .singleElement()
                    .extracting(ToolCallSummary::getDescription)
                    .isEqualTo("Completed: somethingBrandNew");
        }
    }

    @Nested
    @DisplayName("Resource ids and carried-over context")
    class ResourceIds {

        private void modelCalls(String toolName, String input, String result) {
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", toolName, input),
                    TestDataFactory.llmEndTurn("done"));
            when(toolExecutionService.executeTool(eq(toolName), any())).thenReturn(result);
        }

        @Test
        @DisplayName("placeOrder yields the orderId, which is remembered for the rest of the session")
        void placeOrderYieldsTheOrderId() {
            modelCalls("placeOrder", "{\"addressId\":5}", "{\"orderId\":1001}");

            ChatResponse response = agentChatService.chat(request("place my order"));

            verify(agentSessionService).updateActionResult(
                    TestDataFactory.ACTION_ID, ActionStatus.SUCCESS, "{\"orderId\":1001}", "1001", null);

            verify(sessionContextService).save(contextCaptor.capture());
            assertThat(contextCaptor.getValue().getLastOrderId()).isEqualTo("1001");

            assertThat(response.getActionSummary().getActionType()).isEqualTo("ORDER_PLACED");
            assertThat(response.getActionSummary().getDetails())
                    .isEqualTo("Your order has been placed successfully");
        }

        @Test
        @DisplayName("buyNow behaves the same way")
        void buyNowBehavesTheSameWay() {
            modelCalls("buyNow", "{\"productId\":7}", "{\"orderId\":2002}");

            ChatResponse response = agentChatService.chat(request("just buy it"));

            verify(sessionContextService).save(contextCaptor.capture());
            assertThat(contextCaptor.getValue().getLastOrderId()).isEqualTo("2002");
            assertThat(response.getActionSummary().getActionType()).isEqualTo("ORDER_PLACED");
        }

        @Test
        @DisplayName("initiatePayment yields the paymentId")
        void initiatePaymentYieldsThePaymentId() {
            modelCalls("initiatePayment", "{\"orderId\":1001,\"amount\":8999}",
                    "{\"paymentId\":\"pay_ABC\"}");

            ChatResponse response = agentChatService.chat(request("pay now"));

            verify(agentSessionService).updateActionResult(
                    eq(TestDataFactory.ACTION_ID), eq(ActionStatus.SUCCESS), anyString(), eq("pay_ABC"), isNull());

            assertThat(response.getActionSummary().getActionType()).isEqualTo("PAYMENT_INITIATED");
        }

        @Test
        @DisplayName("a tool with no resource of its own reports none")
        void aToolWithNoResourceReportsNone() {
            modelCalls("searchProducts", "{\"search\":\"speaker\"}", "[{\"id\":7}]");

            agentChatService.chat(request("find speakers"));

            verify(agentSessionService).updateActionResult(
                    eq(TestDataFactory.ACTION_ID), eq(ActionStatus.SUCCESS), anyString(), isNull(), isNull());
        }

        @Test
        @DisplayName("a non-JSON tool answer never breaks the extraction")
        void aNonJsonAnswerNeverBreaksExtraction() {
            modelCalls("placeOrder", "{\"addressId\":5}", "order placed!");

            agentChatService.chat(request("place my order"));

            verify(agentSessionService).updateActionResult(
                    eq(TestDataFactory.ACTION_ID), eq(ActionStatus.SUCCESS), anyString(), isNull(), isNull());
        }

        @Test
        @DisplayName("the productId the model used is remembered so follow-ups work")
        void theProductIdIsRemembered() {
            modelCalls("getProductDetails", "{\"productId\":7}", "{\"id\":7}");

            agentChatService.chat(request("tell me about it"));

            verify(sessionContextService).save(contextCaptor.capture());
            assertThat(contextCaptor.getValue().getLastProductId()).isEqualTo("7");
        }

        @Test
        @DisplayName("the variantId the model used is remembered too")
        void theVariantIdIsRemembered() {
            modelCalls("getVariantInfo", "{\"productId\":7,\"variantId\":9}", "{\"inStock\":true}");

            agentChatService.chat(request("is the black one in stock?"));

            verify(sessionContextService).save(contextCaptor.capture());
            assertThat(contextCaptor.getValue().getLastVariantId()).isEqualTo("9");
        }

        @Test
        @DisplayName("a search without a productId leaves the remembered product untouched")
        void aSearchWithoutAProductIdChangesNothing() {
            context.setLastProductId("3");
            modelCalls("searchProducts", "{\"search\":\"speaker\"}", "[]");

            agentChatService.chat(request("find speakers"));

            verify(sessionContextService).save(contextCaptor.capture());
            assertThat(contextCaptor.getValue().getLastProductId()).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("Clarification")
    class Clarification {

        private ChatResponse modelPausesWith(String text) {
            llm.willReturn(TestDataFactory.llmPauseTurn(text));
            return agentChatService.chat(request("I want a speaker"));
        }

        @Test
        @DisplayName("a paused turn flips the session to CLARIFYING and records the topic")
        void aPausedTurnFlipsTheSession() {
            ChatResponse response = modelPausesWith("Which colour would you like?");

            assertThat(response.getMessage()).isEqualTo("Which colour would you like?");
            assertThat(response.getSessionStatus()).isEqualTo(SessionStatus.CLARIFYING);
            assertThat(response.getClarificationNeeded()).isEqualTo("COLOR");
            assertThat(response.getQuickReplies()).containsExactly("Black", "white", "Silver", "Other");

            verify(agentSessionService).markAsClarifying(SESSION_ID, "COLOR");
            verify(agentSessionService).saveMessage(
                    session, MessageRole.ASSISTANT, "Which colour would you like?", null, null, null);
        }

        @Test
        @DisplayName("the reported status never contradicts the clarification flag")
        void theReportedStatusNeverContradictsTheFlag() {
            ChatResponse response = modelPausesWith("Which colour would you like?");

            assertThat(response.getClarificationNeeded()).isNotNull();
            assertThat(response.getSessionStatus()).isEqualTo(SessionStatus.CLARIFYING);
        }

        @Test
        @DisplayName("recognises a size question")
        void recognisesASizeQuestion() {
            ChatResponse response = modelPausesWith("What size do you need?");

            assertThat(response.getClarificationNeeded()).isEqualTo("SIZE");
            assertThat(response.getQuickReplies()).containsExactly("Small", "Medium", "Large", "XL");
        }

        @Test
        @DisplayName("recognises an address question")
        void recognisesAnAddressQuestion() {
            ChatResponse response = modelPausesWith("Where should I deliver it?");

            assertThat(response.getClarificationNeeded()).isEqualTo("ADDRESS");
            assertThat(response.getQuickReplies()).containsExactly("Use saved address", "Add new address");
        }

        @Test
        @DisplayName("recognises an order confirmation")
        void recognisesAnOrderConfirmation() {
            ChatResponse response = modelPausesWith("Shall I confirm this order?");

            assertThat(response.getClarificationNeeded()).isEqualTo("CONFIRM_ORDER");
            assertThat(response.getQuickReplies()).containsExactly("Yes, confirm order", "No cancel");
        }

        @Test
        @DisplayName("recognises a quantity question")
        void recognisesAQuantityQuestion() {
            ChatResponse response = modelPausesWith("How many would you like?");

            assertThat(response.getClarificationNeeded()).isEqualTo("QUANTITY");
            assertThat(response.getQuickReplies()).containsExactly("1", "2", "3");
        }

        @Test
        @DisplayName("an unclassifiable question falls back to GENERAL with no quick replies")
        void anUnclassifiableQuestionIsGeneral() {
            ChatResponse response = modelPausesWith("Could you tell me a bit more?");

            assertThat(response.getClarificationNeeded()).isEqualTo("GENERAL");
            assertThat(response.getQuickReplies()).isNull();
        }

        @Test
        @DisplayName("the British spelling is understood as well")
        void theBritishSpellingIsUnderstood() {
            assertThat(modelPausesWith("Which colour?").getClarificationNeeded()).isEqualTo("COLOR");
        }

        @Test
        @DisplayName("answering the question moves the session back to ACTIVE")
        void answeringMovesTheSessionBackToActive() {
            session.setStatus(SessionStatus.CLARIFYING);
            context.setPendingClarificationFor("COLOR");
            llm.willReturn(TestDataFactory.llmEndTurn("Great - the black one it is."));

            ChatResponse response = agentChatService.chat(request("black"));

            verify(agentSessionService).updateSessionStatus(SESSION_ID, SessionStatus.ACTIVE);
            assertThat(response.getSessionStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(response.getClarificationNeeded()).isNull();
        }

        @Test
        @DisplayName("an unanswered clarification leaves the session CLARIFYING")
        void anUnansweredClarificationStays() {
            session.setStatus(SessionStatus.CLARIFYING);
            context.setPendingClarificationFor("COLOR");
            llm.willReturn(TestDataFactory.llmPauseTurn("Which colour would you like?"));

            ChatResponse response = agentChatService.chat(request("hmm"));

            verify(agentSessionService, never()).updateSessionStatus(SESSION_ID, SessionStatus.ACTIVE);
            assertThat(response.getSessionStatus()).isEqualTo(SessionStatus.CLARIFYING);
        }
    }

    @Nested
    @DisplayName("Session resolution")
    class SessionResolution {

        @Test
        @DisplayName("an unknown sessionId is a 404")
        void anUnknownSessionIsA404() {
            when(agentSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentChatService.chat(request("hi")))
                    .isInstanceOf(SessionNotFoundException.class);

            assertThat(llm.callCount()).isZero();
        }

        @Test
        @DisplayName("chatting into somebody else's session is a 403")
        void somebodyElsesSessionIsA403() {
            when(agentSessionRepository.findById(SESSION_ID))
                    .thenReturn(Optional.of(TestDataFactory.session(SESSION_ID, OTHER_USER_ID)));

            assertThatThrownBy(() -> agentChatService.chat(request("hi")))
                    .isInstanceOf(UnauthorizedSessionAccessException.class);

            verifyNoInteractions(toolExecutionService);
            assertThat(llm.callCount()).isZero();
        }

        @Test
        @DisplayName("without a sessionId the live session in Redis is reused")
        void reusesTheLiveSession() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID)).thenReturn(Optional.of(context));
            llm.willReturn(TestDataFactory.llmEndTurn("ok"));

            ChatResponse response = agentChatService.chat(TestDataFactory.chatRequest(null, "hi"));

            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
            verify(agentSessionService, never()).startSession(any());
        }

        @Test
        @DisplayName("a Redis context pointing at a session Postgres never heard of is an error")
        void anInconsistentContextIsAnError() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID)).thenReturn(Optional.of(context));
            when(agentSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentChatService.chat(TestDataFactory.chatRequest(null, "hi")))
                    .isInstanceOf(AgentException.class)
                    .hasMessage("Session state inconsistency");
        }

        @Test
        @DisplayName("a first-time user gets a session opened for them")
        void aFirstTimeUserGetsASession() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(context));
            llm.willReturn(TestDataFactory.llmEndTurn("Hello! How can I help?"));

            ChatResponse response = agentChatService.chat(TestDataFactory.chatRequest(null, "hi"));

            verify(agentSessionService).startSession(any(StartSessionRequest.class));
            assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
        }

        @Test
        @DisplayName("a session that could not be opened is reported clearly")
        void aSessionThatCouldNotBeOpened() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentChatService.chat(TestDataFactory.chatRequest(null, "hi")))
                    .isInstanceOf(AgentException.class)
                    .hasMessage("Failed to create session");
        }

        @Test
        @DisplayName("a session created in Redis but missing in Postgres is reported clearly")
        void aSessionMissingInPostgres() {
            when(sessionContextService.findByActiveSessionUserId(USER_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(context));
            when(agentSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> agentChatService.chat(TestDataFactory.chatRequest(null, "hi")))
                    .isInstanceOf(AgentException.class)
                    .hasMessage("Failed to create session");
        }
    }

    @Nested
    @DisplayName("Rebuilding an expired context")
    class RebuildingAnExpiredContext {

        private ConversationHistoryResponse history(MessageResponse... messages) {
            return ConversationHistoryResponse.builder()
                    .sessionId(SESSION_ID)
                    .totalMessages(messages.length)
                    .messages(List.of(messages))
                    .build();
        }

        private MessageResponse stored(MessageRole role, String content) {
            MessageResponse message = new MessageResponse();
            message.setRole(role);
            message.setContent(content);
            return message;
        }

        @Test
        @DisplayName("replays the Postgres history, dropping the tool noise")
        void replaysThePostgresHistory() {
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(agentSessionService.getConversationHistory(SESSION_ID)).thenReturn(history(
                    stored(MessageRole.USER, "I want a speaker"),
                    stored(MessageRole.TOOL_RESULT, "[{\"id\":7}]"),
                    stored(MessageRole.ASSISTANT, "I found three")));
            llm.willReturn(TestDataFactory.llmEndTurn("Welcome back"));

            ChatResponse response = agentChatService.chat(request("the black one please"));

            assertThat(response.getMessage()).isEqualTo("Welcome back");

            // the context is saved once while rebuilding and once at the end of the turn
            verify(sessionContextService, times(2)).save(contextCaptor.capture());

            assertThat(contextCaptor.getValue().getConversationMessages())
                    .extracting(ConversationMessage::getRole, ConversationMessage::getContent)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("user", "I want a speaker"),
                            org.assertj.core.groups.Tuple.tuple("assistant", "I found three"),
                            org.assertj.core.groups.Tuple.tuple("user", "the black one please"),
                            org.assertj.core.groups.Tuple.tuple("assistant", "Welcome back"));
        }

        @Test
        @DisplayName("only the human-readable turns are replayed to the model")
        void onlyHumanReadableTurnsAreReplayed() throws Exception {
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(agentSessionService.getConversationHistory(SESSION_ID)).thenReturn(history(
                    stored(MessageRole.USER, "I want a speaker"),
                    stored(MessageRole.TOOL_CALL, "{\"search\":\"speaker\"}"),
                    stored(MessageRole.TOOL_RESULT, "[{\"id\":7}]"),
                    stored(MessageRole.ASSISTANT, "I found three")));
            llm.willReturn(TestDataFactory.llmEndTurn("Welcome back"));

            agentChatService.chat(request("the black one please"));

            JsonNode messages = new ObjectMapper().readTree(llm.lastRequestBody()).path("messages");

            assertThat(messages).hasSize(3);
            assertThat(messages.get(0).path("content").asText()).isEqualTo("I want a speaker");
            assertThat(messages.get(1).path("content").asText()).isEqualTo("I found three");
            assertThat(messages.get(2).path("content").asText()).isEqualTo("the black one please");
        }

        @Test
        @DisplayName("the rebuilt conversation is mutable, so the new turn can be appended")
        void theRebuiltConversationIsMutable() {
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(agentSessionService.getConversationHistory(SESSION_ID))
                    .thenReturn(history(stored(MessageRole.USER, "I want a speaker")));
            llm.willReturn(TestDataFactory.llmEndTurn("Welcome back"));

            agentChatService.chat(request("the black one please"));

            verify(sessionContextService, times(2)).save(contextCaptor.capture());

            assertThat(contextCaptor.getAllValues().get(1).getConversationMessages())
                    .extracting(ConversationMessage::getContent)
                    .containsExactly("I want a speaker", "the black one please", "Welcome back");
        }

        @Test
        @DisplayName("the rebuilt context is stamped with the right owner")
        void theRebuiltContextHasTheRightOwner() {
            when(sessionContextService.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
            when(agentSessionService.getConversationHistory(SESSION_ID))
                    .thenReturn(history(stored(MessageRole.USER, "hi")));
            llm.willReturn(TestDataFactory.llmEndTurn("hello"));

            agentChatService.chat(request("hi again"));

            verify(sessionContextService, times(2)).save(contextCaptor.capture());
            SessionContext rebuilt = contextCaptor.getAllValues().get(0);

            assertThat(rebuilt.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(rebuilt.getUserId()).isEqualTo(USER_ID);
        }
    }

    @Nested
    @DisplayName("Defensive behaviour")
    class DefensiveBehaviour {

        @Test
        @DisplayName("an unexpected stop_reason ends the turn politely")
        void anUnexpectedStopReasonEndsPolitely() {
            llm.willReturn(TestDataFactory.llmUnknownStopReason());

            ChatResponse response = agentChatService.chat(request("hi"));

            assertThat(response.getMessage()).isEqualTo("I encountered an issue. Could you please try again?");
            assertThat(llm.callCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("a runaway tool loop is cut off after ten iterations")
        void aRunawayLoopIsCutOff() {
            llm.willAlwaysReturn(TestDataFactory.llmToolUse("toolu_1", "searchProducts", "{}"));
            when(toolExecutionService.executeTool(anyString(), any())).thenReturn("[]");

            ChatResponse response = agentChatService.chat(request("find me everything"));

            assertThat(response.getMessage())
                    .isEqualTo("I've been thinking too long. Could you please rephrase your request?");
            assertThat(llm.callCount()).isEqualTo(10);
            verify(toolExecutionService, times(10)).executeTool(eq("searchProducts"), any());
        }

        @Test
        @DisplayName("a non-200 from Anthropic surfaces as an AgentException")
        void aNon200SurfacesAsAnAgentException() {
            llm.withStatus(529).willAlwaysReturn("{\"error\":\"overloaded\"}");

            assertThatThrownBy(() -> agentChatService.chat(request("hi")))
                    .isInstanceOf(AgentException.class)
                    .hasMessage("LLM api error: 529");
        }

        @Test
        @DisplayName("an unreachable Anthropic endpoint surfaces as an AgentException with the cause")
        void anUnreachableEndpointSurfaces() {
            ReflectionTestUtils.setField(agentChatService, "LLL_URL", "http://127.0.0.1:1/v1/messages");

            assertThatThrownBy(() -> agentChatService.chat(request("hi")))
                    .isInstanceOf(AgentException.class)
                    .hasMessage("Failed to call LLM api")
                    .hasCauseInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("a response that is not JSON surfaces as an AgentException")
        void aNonJsonResponseSurfaces() {
            llm.willReturn("<html>502 Bad Gateway</html>");

            assertThatThrownBy(() -> agentChatService.chat(request("hi")))
                    .isInstanceOf(AgentException.class)
                    .hasMessageContaining("Failed to parse LLM response as json");
        }

        @Test
        @DisplayName("the user turn is persisted even when the model call later fails")
        void theUserTurnIsPersistedEvenOnFailure() {
            llm.withStatus(500).willAlwaysReturn("{}");

            assertThatThrownBy(() -> agentChatService.chat(request("hi")))
                    .isInstanceOf(AgentException.class);

            verify(agentSessionService).saveMessage(session, MessageRole.USER, "hi", null, null, null);
        }

        @Test
        @DisplayName("nothing is executed when the model asks for no tool at all")
        void nothingIsExecutedWithoutAToolBlock() {
            llm.willReturn("{\"stop_reason\":\"tool_use\",\"content\":[{\"type\":\"text\",\"text\":\"hm\"}]}",
                    TestDataFactory.llmEndTurn("sorry about that"));

            ChatResponse response = agentChatService.chat(request("hi"));

            verifyNoInteractions(toolExecutionService);
            assertThat(response.getMessage()).isEqualTo("sorry about that");
        }
    }

    @Nested
    @DisplayName("Several tools in one turn")
    class SeveralToolsInOneTurn {

        @Test
        @DisplayName("runs each tool the model asks for, in order")
        void runsEachToolInOrder() {
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "searchProducts", "{\"search\":\"speaker\"}"),
                    TestDataFactory.llmToolUse("toolu_2", "getVariantInfo",
                            "{\"productId\":7,\"variantId\":9}"),
                    TestDataFactory.llmToolUse("toolu_3", "addToCart",
                            "{\"productId\":7,\"variantId\":9,\"quantity\":1}"),
                    TestDataFactory.llmEndTurn("Done - the JBL speaker is in your cart."));

            when(toolExecutionService.executeTool(eq("searchProducts"), any())).thenReturn("[{\"id\":7}]");
            when(toolExecutionService.executeTool(eq("getVariantInfo"), any())).thenReturn("{\"inStock\":true}");
            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn("{\"cartItemId\":9}");

            ChatResponse response = agentChatService.chat(request("buy me the black JBL"));

            assertThat(llm.callCount()).isEqualTo(4);
            assertThat(response.getToolCallsMade())
                    .extracting(ToolCallSummary::getToolName)
                    .containsExactly("searchProducts", "getVariantInfo", "addToCart");
            assertThat(response.getToolCallsMade())
                    .extracting(ToolCallSummary::getDescription)
                    .containsExactly("Found products matching your query",
                            "Checked stock availability",
                            "Item added to cart");
        }

        @Test
        @DisplayName("the action summary reports the first successful significant action")
        void theSummaryReportsTheFirstSignificantAction() {
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "addToCart",
                            "{\"productId\":7,\"variantId\":9,\"quantity\":1}"),
                    TestDataFactory.llmToolUse("toolu_2", "placeOrder", "{\"addressId\":5}"),
                    TestDataFactory.llmEndTurn("Order placed"));

            when(toolExecutionService.executeTool(eq("addToCart"), any())).thenReturn("{\"cartItemId\":9}");
            when(toolExecutionService.executeTool(eq("placeOrder"), any())).thenReturn("{\"orderId\":1001}");

            assertThat(agentChatService.chat(request("buy it")).getActionSummary().getActionType())
                    .isEqualTo("ADD_TO_CART");
        }

        @Test
        @DisplayName("a failed tool is skipped when the summary is built")
        void aFailedToolIsSkipped() {
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "addToCart",
                            "{\"productId\":7,\"variantId\":9,\"quantity\":1}"),
                    TestDataFactory.llmToolUse("toolu_2", "placeOrder", "{\"addressId\":5}"),
                    TestDataFactory.llmEndTurn("Order placed"));

            when(toolExecutionService.executeTool(eq("addToCart"), any()))
                    .thenReturn(TestDataFactory.toolFailure("addToCart", "down"));
            when(toolExecutionService.executeTool(eq("placeOrder"), any())).thenReturn("{\"orderId\":1001}");

            assertThat(agentChatService.chat(request("buy it")).getActionSummary().getActionType())
                    .isEqualTo("ORDER_PLACED");
        }

        @Test
        @DisplayName("a turn made only of read-only tools has no action summary")
        void aReadOnlyTurnHasNoSummary() {
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "searchProducts", "{\"search\":\"speaker\"}"),
                    TestDataFactory.llmEndTurn("Here are three"));
            when(toolExecutionService.executeTool(eq("searchProducts"), any())).thenReturn("[]");

            ChatResponse response = agentChatService.chat(request("find speakers"));

            assertThat(response.getToolCallsMade()).hasSize(1);
            assertThat(response.getActionSummary()).isNull();
        }
    }

    @Nested
    @DisplayName("Tool result encoding")
    class ToolResultEncoding {

        @Test
        @DisplayName("quotes inside a tool result are escaped before going back to the model")
        void quotesAreEscaped() {
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "searchProducts", "{\"search\":\"speaker\"}"),
                    TestDataFactory.llmEndTurn("done"));
            when(toolExecutionService.executeTool(eq("searchProducts"), any()))
                    .thenReturn("[{\"name\":\"JBL Flip 6\"}]");

            agentChatService.chat(request("find speakers"));

            verify(sessionContextService).save(contextCaptor.capture());
            ConversationMessage toolResult = contextCaptor.getValue().getConversationMessages().get(2);

            assertThat(toolResult.getContent()).contains("\\\"name\\\"");
            assertThat(toolResult.getToolName()).isEqualTo("tool_result");
        }

        @Test
        @DisplayName("a tool result is replayed to the model as a user turn")
        void aToolResultIsReplayedAsAUserTurn() throws Exception {
            llm.willReturn(
                    TestDataFactory.llmToolUse("toolu_1", "searchProducts", "{\"search\":\"speaker\"}"),
                    TestDataFactory.llmEndTurn("done"));
            when(toolExecutionService.executeTool(eq("searchProducts"), any())).thenReturn("[]");

            agentChatService.chat(request("find speakers"));

            JsonNode body = new ObjectMapper().readTree(llm.requestBodies().get(1));

            assertThat(body.path("messages")).hasSize(3);
            assertThat(body.path("messages").get(2).path("role").asText()).isEqualTo("user");
            assertThat(body.path("messages").get(2).path("content").asText()).contains("tool_result");
        }
    }
}




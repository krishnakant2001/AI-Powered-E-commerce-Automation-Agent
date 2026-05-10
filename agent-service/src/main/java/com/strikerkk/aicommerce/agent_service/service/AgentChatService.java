package com.strikerkk.aicommerce.agent_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.agent_service.auth.UserContext;
import com.strikerkk.aicommerce.agent_service.dto.request.ChatRequest;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import com.strikerkk.aicommerce.agent_service.dto.response.ChatResponse;
import com.strikerkk.aicommerce.agent_service.dto.summary.ToolCallSummary;
import com.strikerkk.aicommerce.agent_service.entity.AgentAction;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.exception.AgentException;
import com.strikerkk.aicommerce.agent_service.llm.SystemPromptBuilder;
import com.strikerkk.aicommerce.agent_service.llm.ToolDefinitionBuilder;
import com.strikerkk.aicommerce.agent_service.llm.ToolExecutionService;
import com.strikerkk.aicommerce.agent_service.model.ConversationMessage;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentSessionRepository agentSessionRepository;
    private final SessionContextService sessionContextService;
    private final AgentSessionService agentSessionService;
    private final SystemPromptBuilder systemPrompt;
    private final ToolDefinitionBuilder toolDefinition;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;

    private String llmKey;
    private String model;
    private String maxTokens;

    private static final int MAX_TOOL_CALLS = 10;

    private static final String LLL_URL = "";

    public ChatResponse chat(ChatRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());
        log.info("Processing chat for userId={} sessionId={}", userId, request.getSessionId());

        // Step 1: Resolve session
        AgentSession session = resolveSession(userId, request.getSessionId());
        UUID sessionId = session.getSessionId();

        // Step 2: Load SessionContext from Redis
        SessionContext sessionContext = sessionContextService.findBySessionId(sessionId)
                .orElseGet(() -> rebuildContextFromPostgres(session, userId));

        // Step 3: Append user message to Redis context
        ConversationMessage newUserMessage = ConversationMessage.builder()
                .role("user")
                .content(request.getMessage())
                .build();
        sessionContext.getConversationMessages().add(newUserMessage);

        // Save user message to postgres
        agentSessionService.saveMessage(session, MessageRole.USER, request.getMessage(), null, null, null);


        // Step 4: LLM orchestration loop
        String finalResponse = null;
        List<ToolCallSummary> toolCallSummaries = new ArrayList<>();
        int loopCount = 0;

        while(loopCount < MAX_TOOL_CALLS) {
            loopCount++;
            log.info("LLM loop iteration {} for sessionId={}", loopCount, sessionId);

            // Call API
            String llmResponseRaw = callLlmApi(sessionContext.getConversationMessages());
            JsonNode llmResponse = parseJson(llmResponseRaw);

            // Parse what LLM returned
            JsonNode contentArray = llmResponse.path("content");
            String stopReason = llmResponse.path("stop_reason").asText();

            // LLM returned a final text response
            if ("end_turn".equals(stopReason)) {
                finalResponse = extractTextFromContent(contentArray);

                // Append assistant response to Redis context
                sessionContext.getConversationMessages().add(
                        ConversationMessage.builder()
                                .role("assistant")
                                .content(finalResponse)
                                .build()
                );

                // Save to Postgres
                agentSessionService.saveMessage(
                        session, MessageRole.ASSISTANT,
                        finalResponse, null, null, null
                );

                // Clear any pending clarification if agent gave a final answer
                sessionContext.setPendingClarificationFor(null);
                break;
            }

            // LLM wants to call a tool
            if("tool_use".equals(stopReason)) {
                for(JsonNode contentBlock: contentArray) {
                    if("tool_use".equals(contentBlock.path("type").asText())) {
                        String toolName = contentBlock.path("name").asText();
                        String toolUseId = contentBlock.path("id").asText();
                        JsonNode toolInput = contentBlock.path("input");

                        log.info("LLM wants to call tool={} for sessionId={}", toolName, session);

                        // Save TOOL_CALL message to postgres
                        agentSessionService.saveMessage(
                                session,
                                MessageRole.USER,
                                null,
                                toolName,
                                toolInput.toString(),
                                null
                        );

                        // Log action as PENDING in postgres
                        AgentAction action = agentSessionService.saveAction(
                                session,
                                userId,
                                mapTooNameToActionType(toolName),
                                toolInput.toString()
                        );

                        // Execute the tool against your microservice
                        String toolResult = toolExecutionService.executeTool(toolName, toolInput);

                        // Determine if tool succeed or failed
                        boolean toolFailed = toolResult.contains("\"error\": true");
                        ActionStatus status = toolFailed ? ActionStatus.FAILURE : ActionStatus.SUCCESS;

                        // Extract resourceId if available
                        String resourceId = extractResourceId(toolName, toolResult);

                        // Update action in postgres with result
                        agentSessionService.updateActionResult(
                                action.getActionId(),
                                status,
                                toolResult,
                                resourceId,
                                toolFailed ? toolResult : null
                        );

                        // Save TOOL_RESULT to Postgres
                        agentSessionService.saveMessage(
                                session,
                                MessageRole.TOOL_RESULT,
                                null,
                                toolName,
                                null,
                                toolResult
                        );


                        // Track for chat response summary
                        toolCallSummaries.add(ToolCallSummary.builder()
                                .toolName(toolName)
                                .status(toolFailed ? "FAILED" : "SUCCESS")
                                .description(buildToolDescription(toolName, toolResult, toolFailed))
                                .build());

                        // Store last product or variant Ids in redis context for future turns
                        updateContextFromToolResult(sessionContext, toolName, toolResult, toolInput);


                        // Append tool use + result to conversation history
                        sessionContext.getConversationMessages().add(
                                ConversationMessage.builder()
                                        .role("assistant")
                                        .content(buildToolUseContent(toolUseId, toolName, toolInput.toString()))
                                        .toolName(toolName)
                                        .build()
                        );

                        sessionContext.getConversationMessages().add(
                                ConversationMessage.builder()
                                        .role("user")
                                        .content(buildToolResultContent(toolUseId, toolResult))
                                        .toolName("tool_result")
                                        .build()
                        );
                    }
                }
                continue;
            }

        }

    }

    // ——————————————————————————————————
    // Call LLM API
    // ——————————————————————————————————

    private String callLlmApi(List<ConversationMessage> history) {
        try {
            // Build messages array from conversation history
            List<Object> messages = buildMessagesForApi(history);

            // Build request body
            String requestBody = objectMapper.writeValueAsString(new HashMap<>() {{
                put("model", model);
                put("max_tokens", maxTokens);
                put("system", systemPrompt.build());
                put("tools", toolDefinition.build());
                put("messages", messages);
            }});


            HttpClient client = HttpClient.newHttpClient();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(LLL_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", llmKey)
                    .header("llm-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() != 200) {
                log.error("Claude API returned status={} body={}", response.statusCode(), response.body());
                throw new AgentException("LLM api error: " + response.statusCode());
            }

            return response.body();

        } catch (AgentException agentEx) {
            throw agentEx;
        } catch (Exception ex) {
            throw new AgentException("Failed to call LLM api", ex);
        }
    }

    private List<Object> buildMessagesForApi(List<ConversationMessage> history) {
        List<Object> messages = new ArrayList<>();

        for(ConversationMessage msg : history) {
            if("tool_result".equals(msg.getToolName())) {
                messages.add(Map.of(
                        "role", "user",
                        "content", msg.getContent()
                ));
            }
            else {
                messages.add(Map.of(
                        "role", msg.getRole(),
                        "content", msg.getContent() != null ? msg.getContent() : ""
                ));
            }
        }

        return messages;
    }

    // Resolve session - find existing session by using sessionId and userId
    private AgentSession resolveSession(Long userId, UUID sessionId) {
        if(sessionId != null) {
            return agentSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new AgentException("Session not found " + sessionId));
        }

        // Check if user has an active session in Redis
        Optional<SessionContext> activeSessionContext = sessionContextService.findByActiveSessionUserId(userId);

        if(activeSessionContext.isPresent()) {
            return agentSessionRepository.findById(activeSessionContext.get().getSessionId())
                    .orElseThrow(() -> new AgentException("Session state inconsistency"));
        }

        // Create new session
        StartSessionRequest startRequest = new StartSessionRequest();
        agentSessionService.startSession(startRequest);

        return sessionContextService.findByActiveSessionUserId(userId)
                .map(context -> agentSessionRepository.findById(context.getSessionId())
                        .orElseThrow(() -> new AgentException("Failed to create session")))
                .orElseThrow(() -> new AgentException("Failed to create session"));
    }

    // Redis context has expired - rebuild from postgres message history
    private SessionContext rebuildContextFromPostgres(AgentSession session, Long userId) {
        log.info("Rebuilding SessionContext from Postgres for sessionId={}", session.getSessionId());

        List<ConversationMessage> messages = agentSessionService
                .getConversationHistory(session.getSessionId())
                .getMessages()
                .stream()
                .filter(m -> m.getRole() == MessageRole.USER || m.getRole() == MessageRole.ASSISTANT)
                .map(m -> ConversationMessage.builder()
                        .role(m.getRole() == MessageRole.USER ? "user" : "assistant")
                        .content(m.getContent())
                        .build())
                .toList();

        SessionContext context = SessionContext.builder()
                .sessionId(session.getSessionId())
                .userId(userId)
                .conversationMessages(messages)
                .build();

        sessionContextService.save(context);
        return context;
    }


    private void updateContextFromToolResult(SessionContext sessionContext, String toolName, String toolResult, JsonNode toolInput) {
        try {
            switch (toolName) {
                // Store productId so "make it black" works without re-searching
                case "getProductDetails", "searchProducts" -> {
                    if(toolInput.has("productId")) {
                        sessionContext.setLastProductId(toolInput.path("productId").asText());
                    }
                }
                case "getVariantInfo" -> sessionContext.setLastVariantId(toolInput.path("variantId").asText());

                case "placeOrder", "buyNow" -> {
                    // Store orderId for "where's my order?" in same session
                    String orderId = extractResourceId(toolName, toolResult);
                    if(orderId != null) sessionContext.setLastOrderId(orderId);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not update session context from tool result: {}", ex.getMessage());
        }
    }

    private ActionType mapTooNameToActionType(String toolName) {
        return switch (toolName) {
            case "searchProducts"    -> ActionType.SEARCH_PRODUCT;
            case "getProductDetails" -> ActionType.GET_PRODUCT_DETAILS;
            case "getVariantInfo"    -> ActionType.GET_VARIANT_INFO;
            case "addToCart"         -> ActionType.ADD_TO_CART;
            case "clearCart"         -> ActionType.CLEAR_CART;
            case "placeOrder"        -> ActionType.PLACE_ORDER;
            case "buyNow"            -> ActionType.BUY_NOW;
            case "getOrder"          -> ActionType.GET_ORDER_STATUS;
            case "getMyOrders"       -> ActionType.GET_ORDER_STATUS;
            case "initiatePayment"   -> ActionType.INITIATE_PAYMENT;
            case "getAllAddresses"   -> ActionType.GET_USER_ADDRESS;
            default                  -> ActionType.SEARCH_PRODUCT;
        };
    }

    private String extractTextFromContent(JsonNode contentArray) {
        for(JsonNode content : contentArray) {
            if("text".equals(content.path("type").asText())) {
                return content.path("text").asText();
            }
        }
        return "I'm not sure how to respond to that. Could you rephrase?";
    }

    private String extractResourceId(String toolName, String toolResult) {
        try {
            JsonNode result = objectMapper.readTree(toolResult);
            return switch (toolName) {
                case "placeOrder", "buyNow"   -> result.path("orderId").asText(null);
                case "addToCart"              -> result.path("cartItemId").asText(null);
                case "initiatePayment"        -> result.path("paymentId").asText();
                default                       -> null;
            };
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildToolDescription(String toolName, String result, boolean failed) {
        if (failed) return "Failed: " + toolName;
        return switch (toolName) {
            case "searchProducts"    -> "Found products matching your query";
            case "getProductDetails" -> "Retrieved product details and variants";
            case "getVariantInfo"    -> "Checked stock availability";
            case "addToCart"         -> "Item added to cart";
            case "placeOrder",
                 "buyNow"            -> "Order placed successfully";
            case "initiatePayment"   -> "Payment initiated";
            case "getAllAddresses"   -> "Fetched delivery addresses";
            default                  -> "Completed: " + toolName;
        };
    }

    private String buildToolUseContent(String toolUseId, String toolName, String toolInput) {
        return String.format(
                "[{\"type\":\"tool_use\", \"id\":\"%s\", \"name\":\"%s\", \"input\":\"%s\"}]",
                toolName,
                toolUseId,
                toolInput
        );
    }
    private String buildToolResultContent(String toolUseId, String toolResult) {
        return String.format(
                "[{\"type\":\"tool_result\", \"tool_use_id\":\"%s\", \"content\":\"%s\"}]",
                toolUseId,
                toolResult.replace("\"", "\\\"")
        );
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new AgentException("Failed to parse LLM response as json ", ex);
        }
    }

}

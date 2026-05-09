package com.strikerkk.aicommerce.agent_service.service;

import com.strikerkk.aicommerce.agent_service.auth.UserContext;
import com.strikerkk.aicommerce.agent_service.dto.request.ChatRequest;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import com.strikerkk.aicommerce.agent_service.dto.response.ChatResponse;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import com.strikerkk.aicommerce.agent_service.exception.AgentException;
import com.strikerkk.aicommerce.agent_service.model.ConversationMessage;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentSessionRepository agentSessionRepository;
    private final SessionContextService sessionContextService;
    private final AgentSessionService agentSessionService;

    public ChatResponse chat(ChatRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());

        log.info("Processing chat for userId={} sessionId={}", userId, request.getSessionId());

        AgentSession session = resolveSession(userId, request.getSessionId());
        UUID sessionId = session.getSessionId();

        SessionContext sessionContext = sessionContextService.findBySessionId(sessionId)
                .orElseGet(() -> rebuildContextFromPostgres(session, userId));

        ConversationMessage newUserMessage = ConversationMessage.builder()
                .role("user")
                .content(request.getMessage())
                .build();
        sessionContext.getConversationMessages().add(newUserMessage);

        agentSessionService.saveMessage(session, MessageRole.USER, request.getMessage(), null, null, null);


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

}

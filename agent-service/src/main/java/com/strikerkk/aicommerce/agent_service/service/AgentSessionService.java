package com.strikerkk.aicommerce.agent_service.service;

import com.strikerkk.aicommerce.agent_service.auth.UserContext;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import com.strikerkk.aicommerce.agent_service.dto.response.SessionStatusResponse;
import com.strikerkk.aicommerce.agent_service.dto.response.StartSessionResponse;
import com.strikerkk.aicommerce.agent_service.entity.AgentAction;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import com.strikerkk.aicommerce.agent_service.exception.SessionNotFoundException;
import com.strikerkk.aicommerce.agent_service.exception.UnauthorizedSessionAccessException;
import com.strikerkk.aicommerce.agent_service.model.ConversationMessage;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.repository.AgentActionRepository;
import com.strikerkk.aicommerce.agent_service.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentSessionRepository agentSessionRepository;
    private final AgentActionRepository agentActionRepository;
    private final SessionContextService sessionContextService;

    @Transactional
    public StartSessionResponse startSession(StartSessionRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());
        String userEmail = UserContext.getUserEmail();

        Optional<SessionContext> existingContext = sessionContextService.findByActiveSessionUserId(userId);

        if(existingContext.isPresent()) {
            SessionContext context = existingContext.get();

            log.info("User {} already has active session={}", userId, context.getSessionId());

            StartSessionResponse response = new StartSessionResponse();
            response.setSessionId(context.getSessionId());
            response.setStatus(SessionStatus.ACTIVE);
            response.setMessage("You already have an active session. Continuing from where you left off.");
            response.setCreatedAt(LocalDateTime.now());

            return response;
        }

        // No active session found - Create session in postgres and redis
        AgentSession session = AgentSession.builder()
                .userId(userId)
                .status(SessionStatus.ACTIVE)
                .initialIntent(request != null ? request.getInitialIntent() : null)
                .build();

        AgentSession savedSession = agentSessionRepository.save(session);
        log.info("Create AgentSession in database with sessionId={}", savedSession);

        SessionContext context = SessionContext.builder()
                .sessionId(savedSession.getSessionId())
                .userId(userId)
                .userEmail(userEmail)
                .currentIntent(request != null ? request.getInitialIntent() : null)
                .conversationMessages(new ArrayList<>())
                .lastActivityAt(LocalDateTime.now())
                .build();

        sessionContextService.save(context);

        log.info("Initialized SessionContext in Redis - key=session:{}", savedSession.getSessionId());

        return StartSessionResponse.builder()
                .sessionId(savedSession.getSessionId())
                .status(SessionStatus.ACTIVE)
                .message("Session started. How can I help you today?")
                .createdAt(savedSession.getCreatedAt())
                .build();

    }

    public SessionStatusResponse getSessionStatus(UUID sessionId) {
        Long userId = Long.valueOf(UserContext.getUserId());

        log.info("Getting session status userId={} sessionId={}", userId, sessionId);

        AgentSession session = findAndValidateSession(sessionId, userId);

        int totalMessages = agentSessionRepository.countBySession_SessionId(sessionId);
        List<AgentAction> actions = agentActionRepository.findBySession_SessionIdOrderByCreatedAtAsc(sessionId);


        // Get the live status from redis

        String pendingClarificationFor = null;
        LocalDateTime lastActivityAt = session.getUpdatedAt();
        String lastAgentMessage = null;

        Optional<SessionContext> contextOptional = sessionContextService.findBySessionId(sessionId);
        if(contextOptional.isPresent()) {
            SessionContext ctx = contextOptional.get();
            pendingClarificationFor = ctx.getPendingClarificationFor();
            lastActivityAt = ctx.getLastActivityAt();


            // Get the last message from conversation history
            List<ConversationMessage> history = ctx.getConversationMessages();

            for(int i = history.size() - 1; i >= 0; i--) {
                if("assistant".equals(history.get(i).getRole())) {
                    lastAgentMessage = history.get(i).getContent();
                    break;
                }
            }

//            history.stream()
//                    .filter(message -> "assistant".equals(message.getRole()))
//                    .reduce((first, second) -> second)        // keep last
//                    .ifPresent(message -> lastAgentMessage.set(message.getContent()));

        }

        return SessionStatusResponse.builder()
                .sessionId(sessionId)
                .userId(userId)
                .status(session.getStatus())
                .initialIntent(session.getInitialIntent())
                .totalMessages(totalMessages)
                .totalActions(actions.size())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .pendingClarificationFor(pendingClarificationFor)
                .lastAgentMessage(lastAgentMessage)
                .lastActivityAt(lastActivityAt)
                .build();

    }

    @Transactional
    public void endSession(UUID sessionId) {
        Long userId = Long.valueOf(UserContext.getUserId());

        log.info("Ending session for userId={} and sessionId={}", userId, sessionId);

        AgentSession session = findAndValidateSession(sessionId, userId);

        // Only close if not already closed/completed
        if(session.getStatus() == SessionStatus.ACTIVE || session.getStatus() == SessionStatus.CLARIFYING) {
            session.setStatus(SessionStatus.CLOSED);
            agentSessionRepository.save(session);

            log.info("Marked session CLOSED in postgres - sessionId={}", sessionId);
        }


        sessionContextService.delete(sessionId, userId);
        log.info("Deleted SessionContext from redis - sessionId={}", sessionId);

    }

    private AgentSession findAndValidateSession(UUID sessionId, Long userId) {
        AgentSession session = agentSessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId.toString()));

        if(!session.getUserId().equals(userId)) {
            throw new UnauthorizedSessionAccessException(sessionId.toString());
        }

        return session;
    }
}

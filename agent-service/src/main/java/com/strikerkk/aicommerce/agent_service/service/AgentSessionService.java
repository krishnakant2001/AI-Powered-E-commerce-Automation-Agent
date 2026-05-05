package com.strikerkk.aicommerce.agent_service.service;

import com.strikerkk.aicommerce.agent_service.auth.UserContext;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import com.strikerkk.aicommerce.agent_service.dto.response.StartSessionResponse;
import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import com.strikerkk.aicommerce.agent_service.model.SessionContext;
import com.strikerkk.aicommerce.agent_service.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentSessionRepository agentSessionRepository;
    private final SessionContextService sessionContextService;

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

        // No active session found
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
}

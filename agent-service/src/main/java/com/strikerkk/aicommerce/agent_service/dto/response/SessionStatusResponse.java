package com.strikerkk.aicommerce.agent_service.dto.response;

import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SessionStatusResponse {

    private UUID sessionId;
    private Long userId;

    private SessionStatus status;
    private String initialIntent;

    private Integer totalMessages;
    private Integer totalActions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String pendingClarificationFor;

    private String lastAgentMessage;
    private LocalDateTime lastActivityAt;

}

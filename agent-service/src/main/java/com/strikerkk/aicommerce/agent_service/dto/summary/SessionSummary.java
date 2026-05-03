package com.strikerkk.aicommerce.agent_service.dto.summary;

import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SessionSummary {

    private UUID sessionId;
    private SessionStatus status;
    private String initialIntent;

    private String outcome;

    private Integer totalMessages;
    private Integer totalActions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

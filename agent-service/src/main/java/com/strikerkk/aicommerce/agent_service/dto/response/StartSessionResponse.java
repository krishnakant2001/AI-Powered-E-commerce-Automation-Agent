package com.strikerkk.aicommerce.agent_service.dto.response;

import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class StartSessionResponse {

    private UUID sessionId;
    private SessionStatus status;
    private String message;         // "Session started - How can I help you today?"
    private LocalDateTime createdAt;
}

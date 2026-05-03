package com.strikerkk.aicommerce.agent_service.dto.response;

import com.strikerkk.aicommerce.agent_service.entity.enums.ActionStatus;
import com.strikerkk.aicommerce.agent_service.entity.enums.ActionType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ActionResponse {

    private UUID actionId;
    private ActionType actionType;
    private ActionStatus status;

    private String requestPayload;
    private String responsePayload;

    private String resourceId;
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

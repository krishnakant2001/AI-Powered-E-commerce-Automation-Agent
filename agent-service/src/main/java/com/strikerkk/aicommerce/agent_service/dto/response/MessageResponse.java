package com.strikerkk.aicommerce.agent_service.dto.response;

import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MessageResponse {

    private UUID messageId;
    private MessageRole role;
    private String content;

    private String toolName;
    private String toolInput;
    private String toolOutput;

    private Integer sequenceNumber;
    private LocalDateTime createdAt;
}

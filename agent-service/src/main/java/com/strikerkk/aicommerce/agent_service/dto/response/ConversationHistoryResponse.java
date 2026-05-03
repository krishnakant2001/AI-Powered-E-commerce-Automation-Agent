package com.strikerkk.aicommerce.agent_service.dto.response;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ConversationHistoryResponse {

    private UUID sessionId;
    private Integer totalMessages;
    private List<MessageResponse> messages;

}

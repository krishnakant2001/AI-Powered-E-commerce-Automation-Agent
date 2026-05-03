package com.strikerkk.aicommerce.agent_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ChatRequest {

    // sessionId is optional on first message, if null - agent service create new session
    // from second message onward, need to send the sessionId
    private UUID sessionId;

    @NotBlank(message = "Message cannot be blank")
    @Size(max = 1000, message = "Message cannot exceed 1000 characters")
    private String message;

}

package com.strikerkk.aicommerce.agent_service.dto.response;

import com.strikerkk.aicommerce.agent_service.dto.summary.ActionSummary;
import com.strikerkk.aicommerce.agent_service.dto.summary.ToolCallSummary;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ChatResponse {

    private UUID sessionId;
    private String message;                         // Agent reply shown to the user
    private SessionStatus status;
    private String clarificationNeeded;
    private List<String> quickReplies;
    private ActionSummary actionSummary;
    private List<ToolCallSummary> toolCallsMade;    // All the tools the agent called in this turn

}

package com.strikerkk.aicommerce.agent_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SessionActionResponse {

    private UUID sessionId;
    private Integer totalActions;
    private List<ActionResponse> actions;

}

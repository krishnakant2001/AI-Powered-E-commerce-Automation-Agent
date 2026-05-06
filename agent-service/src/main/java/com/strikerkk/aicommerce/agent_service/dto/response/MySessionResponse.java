package com.strikerkk.aicommerce.agent_service.dto.response;

import com.strikerkk.aicommerce.agent_service.dto.summary.SessionSummary;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MySessionResponse {

    private Integer totalSessions;
    private List<SessionSummary> sessions;
}

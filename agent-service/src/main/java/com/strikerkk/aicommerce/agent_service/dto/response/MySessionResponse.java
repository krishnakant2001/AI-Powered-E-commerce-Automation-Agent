package com.strikerkk.aicommerce.agent_service.dto.response;

import com.strikerkk.aicommerce.agent_service.dto.summary.SessionSummary;
import lombok.Data;

import java.util.List;

@Data
public class MySessionResponse {

    private Integer totalSessions;
    private List<SessionSummary> sessions;
}

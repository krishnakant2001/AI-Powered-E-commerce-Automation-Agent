package com.strikerkk.aicommerce.agent_service.dto.summary;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolCallSummary {

    private String toolName;        // "searchProduct", "addToCart"
    private String status;          // "SUCCESS", "FAILED"
    private String description;

}

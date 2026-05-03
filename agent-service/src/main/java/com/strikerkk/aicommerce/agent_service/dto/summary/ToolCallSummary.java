package com.strikerkk.aicommerce.agent_service.dto.summary;

import lombok.Data;

@Data
public class ToolCallSummary {

    private String toolName;        // "searchProduct", "addToCart"
    private String status;          // "SUCCESS", "FAILED"
    private String description;

}

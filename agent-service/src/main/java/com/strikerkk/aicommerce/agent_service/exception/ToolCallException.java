package com.strikerkk.aicommerce.agent_service.exception;

import lombok.Getter;

@Getter
public class ToolCallException extends RuntimeException {

    private final String toolName;

    public ToolCallException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
    }

    public ToolCallException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }


}

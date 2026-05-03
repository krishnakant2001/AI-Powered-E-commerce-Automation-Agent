package com.strikerkk.aicommerce.agent_service.exception;

public class UnauthorizedSessionAccessException extends RuntimeException {
    public UnauthorizedSessionAccessException(String message) {
        super("You are not authorized to access session: " + message);
    }
}

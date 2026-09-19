package com.strikerkk.aicommerce.user_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Custom exceptions")
class CustomExceptionsTest {

    @Test
    @DisplayName("BadRequestException is an unchecked exception that keeps its message")
    void badRequestException() {
        BadRequestException exception = new BadRequestException("invalid payload");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("invalid payload");
        assertThatThrownBy(() -> {
            throw exception;
        }).isInstanceOf(BadRequestException.class).hasMessage("invalid payload");
    }

    @Test
    @DisplayName("ResourceNotFoundException is an unchecked exception that keeps its message")
    void resourceNotFoundException() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Address not found");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("Address not found");
    }

    @Test
    @DisplayName("UnauthorizedException is an unchecked exception that keeps its message")
    void unauthorizedException() {
        UnauthorizedException exception = new UnauthorizedException("not allowed");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("not allowed");
    }
}


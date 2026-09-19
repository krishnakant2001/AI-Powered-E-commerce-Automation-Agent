package com.strikerkk.aicommerce.user_service.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse")
class ErrorResponseTest {

    @Test
    @DisplayName("keeps every field supplied through the builder")
    void shouldKeepBuilderValues() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 30);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(timestamp)
                .status(HttpStatus.UNAUTHORIZED)
                .error("Invalid email or password")
                .path("/auth/login")
                .build();

        assertThat(response.getTimestamp()).isEqualTo(timestamp);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getError()).isEqualTo("Invalid email or password");
        assertThat(response.getPath()).isEqualTo("/auth/login");
    }

    @Test
    @DisplayName("defaults the timestamp to now when it is not supplied")
    void shouldDefaultTimestampToNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .error("Invalid email or password")
                .path("/auth/login")
                .build();

        assertThat(response.getTimestamp()).isNotNull().isAfter(before);
    }
}


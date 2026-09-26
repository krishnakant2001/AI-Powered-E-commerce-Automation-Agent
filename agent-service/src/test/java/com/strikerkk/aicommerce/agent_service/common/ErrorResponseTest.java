package com.strikerkk.aicommerce.agent_service.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse")
class ErrorResponseTest {

    @Test
    @DisplayName("carries the status, the message and the path")
    void carriesEverything() {
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND)
                .error("Session not found")
                .path("/agent/session/abc")
                .build();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getError()).isEqualTo("Session not found");
        assertThat(response.getPath()).isEqualTo("/agent/session/abc");
    }

    @Test
    @DisplayName("the timestamp defaults to now")
    void theTimestampDefaultsToNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ErrorResponse response = ErrorResponse.builder().error("boom").build();

        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getTimestamp()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("an explicit timestamp overrides the default")
    void anExplicitTimestampWins() {
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 12, 0);

        assertThat(ErrorResponse.builder().timestamp(fixed).build().getTimestamp()).isEqualTo(fixed);
    }

    @Test
    @DisplayName("setters work and equals/hashCode come from @Data")
    void settersAndEquality() {
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 12, 0);

        ErrorResponse first = ErrorResponse.builder().timestamp(fixed).build();
        first.setStatus(HttpStatus.BAD_GATEWAY);
        first.setError("Service call failed");
        first.setPath("/agent/chat");

        ErrorResponse second = ErrorResponse.builder()
                .timestamp(fixed)
                .status(HttpStatus.BAD_GATEWAY)
                .error("Service call failed")
                .path("/agent/chat")
                .build();

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
        assertThat(first.toString()).contains("ErrorResponse");
    }
}


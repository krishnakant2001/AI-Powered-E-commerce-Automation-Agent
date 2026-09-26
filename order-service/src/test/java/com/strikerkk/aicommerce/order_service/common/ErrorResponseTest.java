package com.strikerkk.aicommerce.order_service.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse")
class ErrorResponseTest {

    @Test
    @DisplayName("stamps itself with the current time when none is given")
    void stampsItself() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND)
                .error("Order not found")
                .path("/orders/100")
                .build();

        assertThat(response.getTimestamp()).isNotNull().isAfter(before);
    }

    @Test
    @DisplayName("keeps an explicit timestamp")
    void keepsAnExplicitTimestamp() {
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThat(ErrorResponse.builder().timestamp(fixed).build().getTimestamp()).isEqualTo(fixed);
    }

    @Test
    @DisplayName("carries the status, the reason and the path that failed")
    void carriesTheFailure() {
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .error("Product service unavailable")
                .path("/orders/buy-now")
                .build();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getError()).isEqualTo("Product service unavailable");
        assertThat(response.getPath()).isEqualTo("/orders/buy-now");
    }

    @Test
    @DisplayName("leaves everything but the timestamp empty by default")
    void leavesEverythingElseEmpty() {
        ErrorResponse response = ErrorResponse.builder().build();

        assertThat(response.getStatus()).isNull();
        assertThat(response.getError()).isNull();
        assertThat(response.getPath()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("two failures with the same content are equal")
    void equalityIsByContent() {
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 10, 0);

        ErrorResponse first = ErrorResponse.builder()
                .timestamp(fixed).status(HttpStatus.BAD_REQUEST).error("bad").path("/orders").build();
        ErrorResponse second = ErrorResponse.builder()
                .timestamp(fixed).status(HttpStatus.BAD_REQUEST).error("bad").path("/orders").build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.toString()).contains("bad").contains("/orders");
    }

    @Test
    @DisplayName("stays mutable through its setters")
    void staysMutable() {
        ErrorResponse response = ErrorResponse.builder().build();

        response.setStatus(HttpStatus.FORBIDDEN);
        response.setError("denied");
        response.setPath("/orders/1");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getError()).isEqualTo("denied");
        assertThat(response.getPath()).isEqualTo("/orders/1");
    }

    @Test
    @DisplayName("every instance gets its own timestamp")
    void everyInstanceGetsItsOwnTimestamp() {
        ErrorResponse first = ErrorResponse.builder().build();
        ErrorResponse second = ErrorResponse.builder().build();

        assertThat(first.getTimestamp()).isNotNull();
        assertThat(second.getTimestamp()).isNotNull();
        assertThat(first.getTimestamp()).isBeforeOrEqualTo(second.getTimestamp());
    }
}


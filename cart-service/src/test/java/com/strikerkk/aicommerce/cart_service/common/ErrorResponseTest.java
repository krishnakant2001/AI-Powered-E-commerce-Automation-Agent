package com.strikerkk.aicommerce.cart_service.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse")
class ErrorResponseTest {

    @Test
    @DisplayName("the builder populates every field")
    void builderPopulatesEveryField() {
        LocalDateTime now = LocalDateTime.now();

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(now)
                .status(HttpStatus.NOT_FOUND)
                .error("Cart item not found")
                .path("/cart/items/500")
                .build();

        assertThat(response.getTimestamp()).isEqualTo(now);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getError()).isEqualTo("Cart item not found");
        assertThat(response.getPath()).isEqualTo("/cart/items/500");
    }

    @Test
    @DisplayName("the timestamp defaults to 'now' when it is not supplied")
    void timestampHasADefault() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST)
                .error("boom")
                .build();

        assertThat(response.getTimestamp()).isNotNull().isAfter(before);
    }

    @Test
    @DisplayName("two instances built one after the other get their own timestamp")
    void theDefaultIsEvaluatedPerInstance() throws Exception {
        ErrorResponse first = ErrorResponse.builder().error("a").build();
        Thread.sleep(2);
        ErrorResponse second = ErrorResponse.builder().error("b").build();

        assertThat(second.getTimestamp()).isAfterOrEqualTo(first.getTimestamp());
    }

    @Test
    @DisplayName("every setter works")
    void settersWork() {
        ErrorResponse response = ErrorResponse.builder().build();
        LocalDateTime now = LocalDateTime.now();

        response.setTimestamp(now);
        response.setStatus(HttpStatus.FORBIDDEN);
        response.setError("Unauthorized request");
        response.setPath("/cart/clear");

        assertThat(response.getTimestamp()).isEqualTo(now);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getError()).isEqualTo("Unauthorized request");
        assertThat(response.getPath()).isEqualTo("/cart/clear");
    }

    @Test
    @DisplayName("equals / hashCode / toString come from @Data")
    void valueSemantics() {
        LocalDateTime now = LocalDateTime.now();

        ErrorResponse a = ErrorResponse.builder().timestamp(now).status(HttpStatus.NOT_FOUND).error("e").path("/p").build();
        ErrorResponse b = ErrorResponse.builder().timestamp(now).status(HttpStatus.NOT_FOUND).error("e").path("/p").build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.toString()).contains("error=e", "path=/p");
    }
}


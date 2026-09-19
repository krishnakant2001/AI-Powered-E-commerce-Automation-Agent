package com.strikerkk.aicommerce.product_service.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse")
class ErrorResponseTest {

    @Test
    @DisplayName("the builder copies every field")
    void builderCopiesEveryField() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 12, 0);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(timestamp)
                .status(HttpStatus.NOT_FOUND)
                .error("Product not found")
                .path("/products/details/1")
                .build();

        assertThat(response.getTimestamp()).isEqualTo(timestamp);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getError()).isEqualTo("Product not found");
        assertThat(response.getPath()).isEqualTo("/products/details/1");
    }

    @Test
    @DisplayName("stamps the current time when no timestamp is supplied")
    void stampsADefaultTimestamp() {
        LocalDateTime before = LocalDateTime.now();
        ErrorResponse response = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST)
                .error("boom")
                .build();
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.getTimestamp()).isNotNull().isBetween(before, after);
    }

    @Test
    @DisplayName("gives a fresh timestamp to every instance")
    void eachInstanceGetsItsOwnTimestamp() throws Exception {
        ErrorResponse first = ErrorResponse.builder().error("a").build();
        Thread.sleep(2);
        ErrorResponse second = ErrorResponse.builder().error("b").build();

        assertThat(first.getTimestamp()).isNotEqualTo(second.getTimestamp());
    }

    @Test
    @DisplayName("every setter is available")
    void settersAreAvailable() {
        ErrorResponse response = ErrorResponse.builder().build();

        response.setStatus(HttpStatus.FORBIDDEN);
        response.setError("You are not authorised to modify this product");
        response.setPath("/admin/products/update/1");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getError()).isEqualTo("You are not authorised to modify this product");
        assertThat(response.getPath()).isEqualTo("/admin/products/update/1");
    }

    @Test
    @DisplayName("two instances with the same content are equal")
    void equalsAndHashCode() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 12, 0);

        ErrorResponse first = ErrorResponse.builder()
                .timestamp(timestamp).status(HttpStatus.NOT_FOUND).error("boom").path("/p").build();
        ErrorResponse second = ErrorResponse.builder()
                .timestamp(timestamp).status(HttpStatus.NOT_FOUND).error("boom").path("/p").build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }
}


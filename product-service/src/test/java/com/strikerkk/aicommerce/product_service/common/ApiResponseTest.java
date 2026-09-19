package com.strikerkk.aicommerce.product_service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    @DisplayName("success(message, data) builds a successful envelope")
    void successWithData() {
        ProductResponse payload = new ProductResponse();
        payload.setId(1L);

        ApiResponse<ProductResponse> response = ApiResponse.success("Product created successfully", payload);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Product created successfully");
        assertThat(response.getData()).isSameAs(payload);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("success(message) builds a successful envelope without payload")
    void successWithoutData() {
        ApiResponse<Void> response = ApiResponse.success("Product deleted successfully");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Product deleted successfully");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("error(message) builds a failed envelope without payload")
    void error() {
        ApiResponse<Void> response = ApiResponse.error("Product not found");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Product not found");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("stamps a timestamp that is not in the future")
    void stampsTheTimestamp() {
        LocalDateTime before = LocalDateTime.now();
        ApiResponse<Void> response = ApiResponse.success("ok");
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.getTimestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("omits the null payload from the JSON thanks to @JsonInclude(NON_NULL)")
    void omitsNullData() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.error("Product not found"));

        assertThat(json)
                .contains("\"success\":false")
                .contains("\"message\":\"Product not found\"")
                .contains("\"timestamp\"")
                .doesNotContain("\"data\"");
    }

    @Test
    @DisplayName("serialises the payload when there is one")
    void serialisesTheData() throws Exception {
        ProductResponse payload = new ProductResponse();
        payload.setId(7L);
        payload.setName("JBL Flip 6");

        String json = objectMapper.writeValueAsString(ApiResponse.success("ok", payload));

        assertThat(json)
                .contains("\"success\":true")
                .contains("\"data\"")
                .contains("\"id\":7")
                .contains("\"name\":\"JBL Flip 6\"");
    }

    @Test
    @DisplayName("the builder can be used directly")
    void builderIsAvailable() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 12, 0);

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("custom")
                .data("payload")
                .timestamp(timestamp)
                .build();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("custom");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("keeps the success flag in the JSON even when it is false")
    void keepsThePrimitiveSuccessFlag() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.error("boom"));

        assertThat(json).contains("\"success\":false");
    }
}


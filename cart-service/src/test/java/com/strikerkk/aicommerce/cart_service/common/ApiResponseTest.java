package com.strikerkk.aicommerce.cart_service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.cart_service.dto.response.CartResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    @DisplayName("success(message, data) flags the call as successful and carries the payload")
    void successWithData() {
        CartResponse payload = new CartResponse();
        payload.setCartId(7L);

        ApiResponse<CartResponse> response = ApiResponse.success("ok", payload);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("ok");
        assertThat(response.getData()).isSameAs(payload);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("success(message) is the no-content variant used by the delete endpoints")
    void successWithoutData() {
        ApiResponse<Void> response = ApiResponse.success("deleted");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("deleted");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("error(message) flags the call as failed and never carries a payload")
    void error() {
        ApiResponse<Void> response = ApiResponse.error("Cart item not found");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Cart item not found");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("the timestamp is stamped at creation time")
    void timestampIsStampedAtCreation() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ApiResponse<Void> response = ApiResponse.success("ok");

        assertThat(response.getTimestamp()).isAfter(before);
        assertThat(response.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("a null payload is allowed for the success variant too")
    void successAcceptsANullPayload() {
        ApiResponse<CartResponse> response = ApiResponse.success("ok", null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("the builder is still usable directly")
    void builderIsUsable() {
        LocalDateTime now = LocalDateTime.now();

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("m")
                .data("d")
                .timestamp(now)
                .build();

        assertThat(response.getData()).isEqualTo("d");
        assertThat(response.getTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("equals / hashCode / toString come from @Data")
    void valueSemantics() {
        LocalDateTime now = LocalDateTime.now();

        ApiResponse<String> a = ApiResponse.<String>builder().success(true).message("m").data("d").timestamp(now).build();
        ApiResponse<String> b = ApiResponse.<String>builder().success(true).message("m").data("d").timestamp(now).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.toString()).contains("success=true", "message=m");
    }

    // JSON shape ------------------------------------------------------------------

    @Test
    @DisplayName("serialises success, message, data and timestamp")
    void jsonShape() throws Exception {
        CartResponse payload = new CartResponse();
        payload.setCartId(7L);
        payload.setUserId(42L);
        payload.setItems(List.of());

        String json = objectMapper.writeValueAsString(ApiResponse.success("ok", payload));

        assertThat(json)
                .contains("\"success\":true")
                .contains("\"message\":\"ok\"")
                .contains("\"cartId\":7")
                .contains("\"timestamp\"");
    }

    @Test
    @DisplayName("@JsonInclude(NON_NULL) drops the null data node")
    void nullDataIsNotSerialised() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.error("boom"));

        assertThat(json).doesNotContain("\"data\"");
        assertThat(json).contains("\"success\":false", "\"message\":\"boom\"");
    }

    @Test
    @DisplayName("the boolean is serialised as 'success', not as 'isSuccess'")
    void booleanPropertyName() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.success("ok"));

        assertThat(json).contains("\"success\"").doesNotContain("\"isSuccess\"");
    }
}


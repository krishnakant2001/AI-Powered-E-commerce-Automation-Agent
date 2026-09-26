package com.strikerkk.aicommerce.order_service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.order_service.dto.response.OrderResponse;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
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
    @DisplayName("success() flags the answer and carries the payload")
    void successCarriesThePayload() {
        ApiResponse<String> response = ApiResponse.success("Fetched order details successfully", "payload");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Fetched order details successfully");
        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("success() without a payload leaves the data empty")
    void successWithoutAPayload() {
        ApiResponse<Void> response = ApiResponse.success("You order has been cancelled");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("You order has been cancelled");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("error() flags the failure and never carries data")
    void errorNeverCarriesData() {
        ApiResponse<Void> response = ApiResponse.error("Order not found");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Order not found");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("stamps every answer with the moment it was built")
    void stampsEveryAnswer() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ApiResponse<Void> response = ApiResponse.error("boom");

        assertThat(response.getTimestamp()).isAfter(before);
        assertThat(response.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("carries any payload type, an order as well as a list of them")
    void carriesAnyPayloadType() {
        OrderResponse order = new OrderResponse();
        order.setId(100L);
        order.setStatus(OrderStatus.PENDING);

        ApiResponse<OrderResponse> single = ApiResponse.success("ok", order);
        ApiResponse<List<OrderResponse>> many = ApiResponse.success("ok", List.of(order));

        assertThat(single.getData().getId()).isEqualTo(100L);
        assertThat(many.getData()).hasSize(1);
    }

    @Test
    @DisplayName("an empty list is a payload, not a missing one")
    void anEmptyListIsAPayload() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.success("Order summary response", List.of()));

        assertThat(json).contains("\"data\":[]");
    }

    @Test
    @DisplayName("hides the null data of an error from the JSON")
    void hidesNullData() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.error("Order not found"));

        assertThat(json)
                .contains("\"success\":false")
                .contains("\"message\":\"Order not found\"")
                .doesNotContain("\"data\"");
    }

    @Test
    @DisplayName("serializes success, message, data and timestamp")
    void serializesEveryField() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.success("ok", "payload"));

        assertThat(json)
                .contains("\"success\":true")
                .contains("\"message\":\"ok\"")
                .contains("\"data\":\"payload\"")
                .contains("\"timestamp\"");
    }

    @Test
    @DisplayName("can still be built by hand through the builder")
    void canBeBuiltByHand() {
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 10, 0);

        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("manual")
                .data("data")
                .timestamp(fixed)
                .build();

        assertThat(response.getTimestamp()).isEqualTo(fixed);
        assertThat(response.getData()).isEqualTo("data");
    }

    @Test
    @DisplayName("two answers with the same content are equal")
    void equalityIsByContent() {
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 10, 0);

        ApiResponse<String> first = ApiResponse.<String>builder()
                .success(true).message("m").data("d").timestamp(fixed).build();
        ApiResponse<String> second = ApiResponse.<String>builder()
                .success(true).message("m").data("d").timestamp(fixed).build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first.toString()).contains("m").contains("d");
    }

    @Test
    @DisplayName("tolerates a null message")
    void toleratesANullMessage() {
        assertThat(ApiResponse.error(null).getMessage()).isNull();
    }
}


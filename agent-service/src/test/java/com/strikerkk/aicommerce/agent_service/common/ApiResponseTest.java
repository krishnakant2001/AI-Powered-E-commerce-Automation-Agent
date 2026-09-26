package com.strikerkk.aicommerce.agent_service.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.agent_service.dto.response.StartSessionResponse;
import com.strikerkk.aicommerce.agent_service.entity.enums.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    @DisplayName("success(message, data) flags the call as successful and carries the payload")
    void successWithData() {
        StartSessionResponse payload = StartSessionResponse.builder()
                .sessionId(UUID.randomUUID())
                .status(SessionStatus.ACTIVE)
                .build();

        ApiResponse<StartSessionResponse> response = ApiResponse.success("Session started", payload);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Session started");
        assertThat(response.getData()).isSameAs(payload);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("success(message) leaves the payload empty")
    void successWithoutData() {
        ApiResponse<Void> response = ApiResponse.success("Session ended successfully");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Session ended successfully");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("error(message) flags the call as failed and carries no payload")
    void error() {
        ApiResponse<Void> response = ApiResponse.error("Session not found: abc");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Session not found: abc");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("the timestamp is stamped at creation time")
    void theTimestampIsStampedAtCreation() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ApiResponse<Void> response = ApiResponse.success("ok");

        assertThat(response.getTimestamp()).isAfterOrEqualTo(before);
        assertThat(response.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("the builder is still usable directly")
    void theBuilderIsUsable() {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .message("hi")
                .data("payload")
                .build();

        assertThat(response.getData()).isEqualTo("payload");
        assertThat(response.getTimestamp()).isNull();
    }

    @Test
    @DisplayName("null fields are dropped from the JSON")
    void nullFieldsAreDropped() throws Exception {
        assertThat(ApiResponse.class.getAnnotation(JsonInclude.class).value())
                .isEqualTo(JsonInclude.Include.NON_NULL);

        String json = objectMapper.writeValueAsString(ApiResponse.error("boom"));

        assertThat(json).doesNotContain("\"data\"");
        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"message\":\"boom\"");
    }

    @Test
    @DisplayName("serialises the payload inline under \"data\"")
    void serialisesThePayloadInline() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.success("ok", "hello"));

        assertThat(json).contains("\"data\":\"hello\"");
    }

    @Test
    @DisplayName("equals/hashCode come from @Data")
    void equalsAndHashCode() {
        LocalDateTime now = LocalDateTime.now();

        ApiResponse<String> first = ApiResponse.<String>builder()
                .success(true).message("m").data("d").timestamp(now).build();
        ApiResponse<String> second = ApiResponse.<String>builder()
                .success(true).message("m").data("d").timestamp(now).build();

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
        assertThat(first.toString()).contains("ApiResponse");
    }
}


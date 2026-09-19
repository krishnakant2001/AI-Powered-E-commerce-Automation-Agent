package com.strikerkk.aicommerce.user_service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    @Test
    @DisplayName("success(message, data) builds a successful envelope carrying the payload")
    void shouldBuildSuccessWithData() {
        UserResponse payload = TestDataFactory.userResponse();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ApiResponse<UserResponse> response = ApiResponse.success("User created successfully", payload);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User created successfully");
        assertThat(response.getData()).isSameAs(payload);
        assertThat(response.getTimestamp()).isNotNull().isAfter(before);
    }

    @Test
    @DisplayName("success(message) builds a successful envelope without any payload")
    void shouldBuildSuccessWithoutData() {
        ApiResponse<Void> response = ApiResponse.success("Successfully delete the user with id 1");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Successfully delete the user with id 1");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("error(message) builds a failed envelope without any payload")
    void shouldBuildError() {
        ApiResponse<Void> response = ApiResponse.error("Address not found");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Address not found");
        assertThat(response.getData()).isNull();
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("omits the null data field when serialized to JSON")
    void shouldOmitNullDataInJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String json = objectMapper.writeValueAsString(ApiResponse.error("boom"));

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"message\":\"boom\"");
        assertThat(json).doesNotContain("\"data\"");
    }

    @Test
    @DisplayName("keeps the data field when it is not null")
    void shouldKeepDataInJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String json = objectMapper.writeValueAsString(ApiResponse.success("ok", "payload"));

        assertThat(json).contains("\"data\":\"payload\"");
    }
}


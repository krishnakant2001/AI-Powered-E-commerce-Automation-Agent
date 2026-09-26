package com.strikerkk.aicommerce.payment_service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.payment_service.dto.response.VerifyPaymentResponse;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The shared response envelopes")
class ResponseEnvelopeTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    // ==================================================================
    // ApiResponse
    // ==================================================================

    @Nested
    @DisplayName("ApiResponse")
    class ApiResponseTest {

        @Test
        @DisplayName("a success carries the flag, the message and the payload")
        void aSuccessCarriesEverything() {
            ApiResponse<String> response = ApiResponse.success("Payment created successfully", "payload");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Payment created successfully");
            assertThat(response.getData()).isEqualTo("payload");
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("a success without data still succeeds")
        void aSuccessWithoutDataStillSucceeds() {
            ApiResponse<Void> response = ApiResponse.success("Payment verified");

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).isEqualTo("Payment verified");
            assertThat(response.getData()).isNull();
        }

        @Test
        @DisplayName("an error never carries data")
        void anErrorNeverCarriesData() {
            ApiResponse<Void> response = ApiResponse.error("Payment already exists for this order");

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).isEqualTo("Payment already exists for this order");
            assertThat(response.getData()).isNull();
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("is stamped with the moment it was built")
        void isStampedWithTheMoment() {
            LocalDateTime before = LocalDateTime.now();

            ApiResponse<String> response = ApiResponse.success("ok", "data");

            assertThat(response.getTimestamp()).isAfterOrEqualTo(before).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("carries a whole list, the refund endpoint needs it")
        void carriesAWholeList() {
            ApiResponse<List<String>> response = ApiResponse.success("Getting refund list", List.of("a", "b"));

            assertThat(response.getData()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("omits the null data from the JSON, the client never sees 'data': null")
        void omitsNullDataFromTheJson() throws Exception {
            String json = objectMapper.writeValueAsString(ApiResponse.error("boom"));

            assertThat(json).doesNotContain("\"data\"");
            assertThat(json).contains("\"success\":false").contains("\"message\":\"boom\"");
        }

        @Test
        @DisplayName("serialises the payload of a verified payment")
        void serialisesAVerifiedPayment() throws Exception {
            VerifyPaymentResponse payload = new VerifyPaymentResponse();
            payload.setPaymentId(500L);
            payload.setOrderId(100L);
            payload.setStatus(PaymentStatus.SUCCESS);
            payload.setMessage("Payment verified successfully");
            payload.setPaidAt(LocalDateTime.of(2026, 9, 26, 12, 0));

            String json = objectMapper.writeValueAsString(ApiResponse.success("Payment verified", payload));

            assertThat(json).contains("\"paymentId\":500")
                    .contains("\"status\":\"SUCCESS\"")
                    .contains("\"success\":true");
        }

        @Test
        @DisplayName("can also be built field by field through the builder")
        void canBeBuiltFieldByField() {
            ApiResponse<String> response = ApiResponse.<String>builder()
                    .success(true)
                    .message("built by hand")
                    .data("data")
                    .timestamp(LocalDateTime.of(2026, 1, 1, 0, 0))
                    .build();

            assertThat(response.getMessage()).isEqualTo("built by hand");
            assertThat(response.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        }

        @Test
        @DisplayName("two envelopes with the same content are equal")
        void twoEqualEnvelopesAreEqual() {
            LocalDateTime now = LocalDateTime.now();

            ApiResponse<String> first = ApiResponse.<String>builder()
                    .success(true).message("m").data("d").timestamp(now).build();
            ApiResponse<String> second = ApiResponse.<String>builder()
                    .success(true).message("m").data("d").timestamp(now).build();

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }
    }

    // ==================================================================
    // ErrorResponse
    // ==================================================================

    @Nested
    @DisplayName("ErrorResponse")
    class ErrorResponseTest {

        @Test
        @DisplayName("carries the status, the reason and the path")
        void carriesTheStatusReasonAndPath() {
            ErrorResponse response = ErrorResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .error("Order does not belong to this user")
                    .path("/payments/initiate")
                    .build();

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getError()).isEqualTo("Order does not belong to this user");
            assertThat(response.getPath()).isEqualTo("/payments/initiate");
        }

        @Test
        @DisplayName("stamps itself even when the caller forgets to")
        void stampsItself() {
            LocalDateTime before = LocalDateTime.now();

            ErrorResponse response = ErrorResponse.builder().error("boom").build();

            assertThat(response.getTimestamp()).isNotNull().isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("lets the caller override the stamp")
        void letsTheCallerOverrideTheStamp() {
            LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 0, 0);

            assertThat(ErrorResponse.builder().timestamp(fixed).build().getTimestamp()).isEqualTo(fixed);
        }

        @Test
        @DisplayName("two errors with the same content are equal")
        void twoEqualErrorsAreEqual() {
            LocalDateTime now = LocalDateTime.now();

            ErrorResponse first = ErrorResponse.builder()
                    .timestamp(now).status(HttpStatus.NOT_FOUND).error("e").path("/p").build();
            ErrorResponse second = ErrorResponse.builder()
                    .timestamp(now).status(HttpStatus.NOT_FOUND).error("e").path("/p").build();

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }
    }
}


package com.strikerkk.aicommerce.payment_service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderResponse;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderStatus;
import com.strikerkk.aicommerce.payment_service.dto.request.InitiatePaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.request.VerifyPaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.response.InitiatePaymentResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.PaymentResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.RefundResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.VerifyPaymentResponse;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The payment DTOs")
class PaymentDtoTest {

    /** Configured the way Spring Boot configures the mapper of the web layer. */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    // ==================================================================
    // requests
    // ==================================================================

    @Nested
    @DisplayName("InitiatePaymentRequest")
    class Initiate {

        @Test
        @DisplayName("is read from the JSON body of the checkout button")
        void isReadFromTheJsonBody() throws Exception {
            InitiatePaymentRequest request = objectMapper.readValue(
                    "{\"orderId\":100,\"gateway\":\"RAZORPAY\"}", InitiatePaymentRequest.class);

            assertThat(request.getOrderId()).isEqualTo(100L);
            assertThat(request.getGateway()).isEqualTo(PaymentGateway.RAZORPAY);
        }

        @Test
        @DisplayName("tolerates a body that only names the order - the service always uses Razorpay")
        void toleratesABodyWithoutAGateway() throws Exception {
            InitiatePaymentRequest request =
                    objectMapper.readValue("{\"orderId\":100}", InitiatePaymentRequest.class);

            assertThat(request.getOrderId()).isEqualTo(100L);
            assertThat(request.getGateway()).isNull();
        }

        @Test
        @DisplayName("carries no user id, the gateway header names the caller")
        void carriesNoUserId() {
            assertThat(InitiatePaymentRequest.class.getDeclaredFields())
                    .filteredOn(f -> !f.isSynthetic())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactlyInAnyOrder("orderId", "gateway");
        }

        @Test
        @DisplayName("two requests for the same order are equal")
        void twoEqualRequestsAreEqual() {
            InitiatePaymentRequest first = new InitiatePaymentRequest();
            first.setOrderId(100L);
            first.setGateway(PaymentGateway.RAZORPAY);

            InitiatePaymentRequest second = new InitiatePaymentRequest();
            second.setOrderId(100L);
            second.setGateway(PaymentGateway.RAZORPAY);

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }
    }

    @Nested
    @DisplayName("VerifyPaymentRequest")
    class Verify {

        @Test
        @DisplayName("carries exactly the three fields the Razorpay checkout hands back")
        void carriesTheThreeCheckoutFields() throws Exception {
            VerifyPaymentRequest request = objectMapper.readValue("""
                    {"razorpayOrderId":"order_A","razorpayPaymentId":"pay_B","razorpaySignature":"sig_C"}
                    """, VerifyPaymentRequest.class);

            assertThat(request.getRazorpayOrderId()).isEqualTo("order_A");
            assertThat(request.getRazorpayPaymentId()).isEqualTo("pay_B");
            assertThat(request.getRazorpaySignature()).isEqualTo("sig_C");
        }

        @Test
        @DisplayName("declares no other field")
        void declaresNoOtherField() {
            assertThat(VerifyPaymentRequest.class.getDeclaredFields())
                    .filteredOn(f -> !f.isSynthetic())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactlyInAnyOrder(
                            "razorpayOrderId", "razorpayPaymentId", "razorpaySignature");
        }

        @Test
        @DisplayName("reads every field as a String, the ids are opaque")
        void readsEveryFieldAsAString() {
            assertThat(VerifyPaymentRequest.class.getDeclaredFields())
                    .filteredOn(f -> !f.isSynthetic())
                    .allMatch(f -> f.getType() == String.class);
        }
    }

    // ==================================================================
    // responses
    // ==================================================================

    @Nested
    @DisplayName("InitiatePaymentResponse")
    class InitiateResponse {

        @Test
        @DisplayName("carries everything the Razorpay checkout script needs")
        void carriesTheCheckoutParameters() throws Exception {
            InitiatePaymentResponse response = new InitiatePaymentResponse();
            response.setPaymentId(500L);
            response.setGatewayOrderId("order_A");
            response.setAmount(new BigDecimal("8999.00"));
            response.setCurrency("INR");
            response.setGateway(PaymentGateway.RAZORPAY);
            response.setKeyId("rzp_test_dummykey");

            String json = objectMapper.writeValueAsString(response);

            assertThat(json).contains("\"paymentId\":500")
                    .contains("\"gatewayOrderId\":\"order_A\"")
                    .contains("\"currency\":\"INR\"")
                    .contains("\"gateway\":\"RAZORPAY\"")
                    .contains("\"keyId\":\"rzp_test_dummykey\"");
        }

        @Test
        @DisplayName("never declares a field for the key secret")
        void neverDeclaresTheKeySecret() {
            assertThat(InitiatePaymentResponse.class.getDeclaredFields())
                    .filteredOn(f -> !f.isSynthetic())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactlyInAnyOrder(
                            "paymentId", "gatewayOrderId", "amount", "currency",
                            "gateway", "keyId", "paymentToken")
                    .noneMatch(name -> name.toLowerCase().contains("secret"));
        }
    }

    @Nested
    @DisplayName("VerifyPaymentResponse")
    class VerifyResponse {

        @Test
        @DisplayName("tells the browser the verdict and when the money moved")
        void tellsTheVerdict() throws Exception {
            VerifyPaymentResponse response = new VerifyPaymentResponse();
            response.setPaymentId(500L);
            response.setOrderId(100L);
            response.setStatus(PaymentStatus.SUCCESS);
            response.setMessage("Payment verified successfully");
            response.setPaidAt(LocalDateTime.of(2026, 9, 26, 12, 0));

            String json = objectMapper.writeValueAsString(response);

            assertThat(json).contains("\"status\":\"SUCCESS\"")
                    .contains("\"message\":\"Payment verified successfully\"")
                    .contains("2026-09-26T12:00");
        }

        @Test
        @DisplayName("leaves paidAt empty on a failure")
        void leavesPaidAtEmptyOnAFailure() {
            VerifyPaymentResponse response = new VerifyPaymentResponse();
            response.setStatus(PaymentStatus.FAILED);

            assertThat(response.getPaidAt()).isNull();
        }
    }

    @Nested
    @DisplayName("RefundResponse")
    class Refund {

        @Test
        @DisplayName("carries the amount, the status and the gateway reference")
        void carriesTheRefundDetails() throws Exception {
            RefundResponse response = new RefundResponse();
            response.setId(900L);
            response.setPaymentId(500L);
            response.setRefundAmount(new BigDecimal("1000.00"));
            response.setRefundStatus(RefundStatus.SUCCESS);
            response.setGatewayRefundId("rfnd_A");
            response.setReason("Customer changed their mind");
            response.setRefundedAt(LocalDateTime.of(2026, 9, 26, 12, 0));
            response.setCreatedAt(LocalDateTime.of(2026, 9, 26, 11, 0));

            String json = objectMapper.writeValueAsString(response);

            assertThat(json).contains("\"refundAmount\":1000.00")
                    .contains("\"refundStatus\":\"SUCCESS\"")
                    .contains("\"gatewayRefundId\":\"rfnd_A\"");
        }

        @Test
        @DisplayName("points back at the payment it belongs to")
        void pointsBackAtThePayment() {
            RefundResponse response = new RefundResponse();
            response.setPaymentId(500L);

            assertThat(response.getPaymentId()).isEqualTo(500L);
        }
    }

    @Nested
    @DisplayName("PaymentResponse")
    class PaymentView {

        @Test
        @DisplayName("is the full view of a payment row")
        void isTheFullView() {
            PaymentResponse response = new PaymentResponse();
            response.setId(500L);
            response.setOrderId(100L);
            response.setUserId(42L);
            response.setAmount(new BigDecimal("8999.00"));
            response.setStatus(PaymentStatus.SUCCESS);
            response.setGateway(PaymentGateway.RAZORPAY);
            response.setGatewayOrderId("order_A");
            response.setGatewayPaymentId("pay_A");
            response.setRefunds(List.of(RefundStatus.SUCCESS));

            assertThat(response.getId()).isEqualTo(500L);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(response.getRefunds()).containsExactly(RefundStatus.SUCCESS);
        }

        @Test
        @DisplayName("never exposes the gateway signature, it is proof, not information")
        void neverExposesTheSignature() {
            assertThat(PaymentResponse.class.getDeclaredFields())
                    .filteredOn(f -> !f.isSynthetic())
                    .extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("gatewaySignature");
        }
    }

    // ==================================================================
    // the order-service payload
    // ==================================================================

    @Nested
    @DisplayName("OrderResponse")
    class OrderPayload {

        @Test
        @DisplayName("is read from the envelope order-service answers with")
        void isReadFromTheEnvelope() throws Exception {
            OrderResponse response = objectMapper.readValue("""
                    {"id":100,"userId":42,"totalAmount":8999.00,"deliveryCharges":0,
                     "needToPay":8999.00,"status":"PENDING"}
                    """, OrderResponse.class);

            assertThat(response.getId()).isEqualTo(100L);
            assertThat(response.getUserId()).isEqualTo(42L);
            assertThat(response.getNeedToPay()).isEqualByComparingTo(new BigDecimal("8999.00"));
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("it is needToPay that is charged, not totalAmount")
        void chargesNeedToPay() throws Exception {
            OrderResponse response = objectMapper.readValue("""
                    {"id":100,"userId":42,"totalAmount":499.00,"deliveryCharges":40.00,
                     "needToPay":539.00,"status":"PENDING"}
                    """, OrderResponse.class);

            assertThat(response.getNeedToPay())
                    .isEqualByComparingTo(response.getTotalAmount().add(response.getDeliveryCharges()));
        }

        @Test
        @DisplayName("ignores fields payment-service does not care about")
        void ignoresUnknownFields() throws Exception {
            // Feign configures its own lenient mapper; this proves the DTO itself is a plain bean
            // whose only contract is the six fields the payment flow reads.
            assertThat(OrderResponse.class.getDeclaredFields())
                    .filteredOn(f -> !f.isSynthetic())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactlyInAnyOrder(
                            "id", "userId", "totalAmount", "deliveryCharges", "needToPay", "status");
        }
    }

    // ==================================================================
    // the order status the payment flow reasons about
    // ==================================================================

    @Nested
    @DisplayName("OrderStatus")
    class Status {

        @Test
        @DisplayName("mirrors the states of order-service")
        void mirrorsTheOrderServiceStates() {
            assertThat(OrderStatus.values())
                    .containsExactly(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED,
                            OrderStatus.DELIVERED, OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("only PENDING may be paid")
        void onlyPendingMayBePaid() {
            assertThat(OrderStatus.valueOf("PENDING")).isEqualTo(OrderStatus.PENDING);
            assertThat(OrderStatus.PENDING.name()).isEqualTo("PENDING");
        }
    }
}



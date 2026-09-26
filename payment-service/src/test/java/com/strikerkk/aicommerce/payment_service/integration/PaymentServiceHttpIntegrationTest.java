package com.strikerkk.aicommerce.payment_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.strikerkk.aicommerce.payment_service.clients.OrderClient;
import com.strikerkk.aicommerce.payment_service.common.ApiResponse;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderResponse;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderStatus;
import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.Refund;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import com.strikerkk.aicommerce.payment_service.event.PaymentSuccessEvent;
import com.strikerkk.aicommerce.payment_service.repository.PaymentRepository;
import com.strikerkk.aicommerce.payment_service.repository.RefundRepository;
import com.strikerkk.aicommerce.payment_service.support.TestDataFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the whole service through HTTP: the gateway header interceptor, the controllers, the
 * real signature checker, the real repositories on H2 and the exception advice. Only the two
 * things that would leave the machine are replaced: order-service and the Razorpay HTTP client.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Payment service HTTP wiring")
class PaymentServiceHttpIntegrationTest {

    private static final String USER_ID_HEADER = TestDataFactory.USER_ID_HEADER;
    private static final String SIGNATURE_HEADER = TestDataFactory.RAZORPAY_SIGNATURE_HEADER;
    private static final String USER_ID = String.valueOf(TestDataFactory.USER_ID);
    private static final String OTHER_USER_ID = String.valueOf(TestDataFactory.OTHER_USER_ID);
    private static final Long ORDER_ID = TestDataFactory.ORDER_ID;
    private static final String GATEWAY_ORDER_ID = TestDataFactory.GATEWAY_ORDER_ID;
    private static final String GATEWAY_PAYMENT_ID = TestDataFactory.GATEWAY_PAYMENT_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private OrderClient orderClient;

    @MockitoBean
    private RazorpayClient razorpayClient;

    @MockitoBean
    private org.springframework.kafka.core.KafkaTemplate<Long, PaymentSuccessEvent> kafkaTemplate;

    private com.razorpay.OrderClient razorpayOrders;

    @BeforeEach
    void setUp() throws RazorpayException {
        circuitBreakerRegistry.circuitBreaker("order-service-call").reset();

        refundRepository.deleteAll();
        paymentRepository.deleteAll();

        // The Razorpay sub clients are plain public fields, a mock leaves them null.
        razorpayOrders = org.mockito.Mockito.mock(com.razorpay.OrderClient.class);
        razorpayClient.orders = razorpayOrders;
        when(razorpayOrders.create(any(JSONObject.class))).thenReturn(TestDataFactory.razorpayOrder());

        orderIs(TestDataFactory.USER_ID, OrderStatus.PENDING);
    }

    private void orderIs(Long userId, OrderStatus status) {
        OrderResponse order = TestDataFactory.orderResponse(userId, status, TestDataFactory.AMOUNT);
        when(orderClient.getOrderById(anyLong()))
                .thenReturn(ResponseEntity.ok(ApiResponse.success("Order found", order)));
    }

    private Payment storedPayment(Long userId, PaymentStatus status) {
        return paymentRepository.save(Payment.builder()
                .orderId(ORDER_ID)
                .userId(userId)
                .amount(TestDataFactory.AMOUNT)
                .status(status)
                .gateway(PaymentGateway.RAZORPAY)
                .gatewayOrderId(GATEWAY_ORDER_ID)
                .refunds(new ArrayList<>())
                .build());
    }

    // ==================================================================
    // initiate
    // ==================================================================

    @Nested
    @DisplayName("POST /payments/initiate")
    class Initiate {

        @Test
        @DisplayName("creates the gateway order and stores an INITIATED payment")
        void createsTheGatewayOrderAndStoresTheRow() throws Exception {
            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.gatewayOrderId").value(GATEWAY_ORDER_ID))
                    .andExpect(jsonPath("$.data.keyId").value(TestDataFactory.KEY_ID))
                    .andExpect(jsonPath("$.data.currency").value("INR"));

            Payment stored = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.INITIATED);
            assertThat(stored.getUserId()).isEqualTo(TestDataFactory.USER_ID);
            assertThat(stored.getAmount()).isEqualByComparingTo(TestDataFactory.AMOUNT);
        }

        @Test
        @DisplayName("takes the payer from the gateway header, not from the body")
        void takesThePayerFromTheHeader() throws Exception {
            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":100}"))
                    .andExpect(status().isCreated());

            assertThat(paymentRepository.findByOrderId(ORDER_ID).orElseThrow().getUserId())
                    .isEqualTo(TestDataFactory.USER_ID);
        }

        @Test
        @DisplayName("refuses an anonymous call - without the header there is no payer")
        void refusesAnAnonymousCall() throws Exception {
            mockMvc.perform(post("/payments/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));

            assertThat(paymentRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("refuses to pay somebody else's order with a 403")
        void refusesSomebodyElsesOrder() throws Exception {
            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, OTHER_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Order does not belong to this user"));

            assertThat(paymentRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("refuses an order that is not PENDING")
        void refusesANonPendingOrder() throws Exception {
            orderIs(TestDataFactory.USER_ID, OrderStatus.CONFIRMED);

            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message")
                            .value("Order is not in PENDING state, Cannot initiate payment"));
        }

        @Test
        @DisplayName("refuses to charge the same order twice")
        void refusesToChargeTheSameOrderTwice() throws Exception {
            storedPayment(TestDataFactory.USER_ID, PaymentStatus.INITIATED);

            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment already exists for this order"));

            assertThat(paymentRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("forwards the payer to order-service, so it can check the ownership too")
        void forwardsThePayerDownstream() throws Exception {
            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isCreated());

            verify(orderClient).getOrderById(ORDER_ID);
        }

        @Test
        @DisplayName("stores nothing when the gateway rejects the order")
        void storesNothingWhenTheGatewayFails() throws Exception {
            when(razorpayOrders.create(any(JSONObject.class)))
                    .thenThrow(new RazorpayException("gateway is down"));

            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("Failed to create Payment with Razorpay")));

            assertThat(paymentRepository.findAll()).isEmpty();
        }
    }

    // ==================================================================
    // verify
    // ==================================================================

    @Nested
    @DisplayName("POST /payments/verify")
    class Verify {

        @BeforeEach
        void anInitiatedPayment() {
            storedPayment(TestDataFactory.USER_ID, PaymentStatus.INITIATED);
        }

        @Test
        @DisplayName("accepts a genuine Razorpay signature and marks the payment SUCCESS")
        void acceptsAGenuineSignature() throws Exception {
            mockMvc.perform(post("/payments/verify")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.signedVerifyRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.message").value("Payment verified successfully"));

            Payment stored = paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(stored.getGatewayPaymentId()).isEqualTo(GATEWAY_PAYMENT_ID);
            assertThat(stored.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("rejects a forged signature and marks the payment FAILED")
        void rejectsAForgedSignature() throws Exception {
            mockMvc.perform(post("/payments/verify")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.verifyPaymentRequest(
                                    GATEWAY_ORDER_ID, GATEWAY_PAYMENT_ID, "a-forged-signature"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("FAILED"));

            Payment stored = paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(stored.getGatewayErrorCode()).isEqualTo("SIGNATURE_VERIFICATION_FAILED");
            assertThat(stored.getGatewaySignature()).isNull();
        }

        @Test
        @DisplayName("rejects a signature that was computed for another payment")
        void rejectsAReplayedSignature() throws Exception {
            String signatureOfAnotherPayment =
                    TestDataFactory.razorpaySignature(GATEWAY_ORDER_ID, "pay_SOMEONEelse");

            mockMvc.perform(post("/payments/verify")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.verifyPaymentRequest(
                                    GATEWAY_ORDER_ID, GATEWAY_PAYMENT_ID, signatureOfAnotherPayment))))
                    .andExpect(jsonPath("$.data.status").value("FAILED"));
        }

        @Test
        @DisplayName("refuses to verify a payment of another customer")
        void refusesAnotherCustomersPayment() throws Exception {
            mockMvc.perform(post("/payments/verify")
                            .header(USER_ID_HEADER, OTHER_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.signedVerifyRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment does not belong to this user"));

            assertThat(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.INITIATED);
        }

        @Test
        @DisplayName("refuses to verify the same payment twice")
        void refusesToVerifyTwice() throws Exception {
            mockMvc.perform(post("/payments/verify")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.signedVerifyRequest())))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/payments/verify")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.signedVerifyRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment already verified successfully"));
        }

        @Test
        @DisplayName("refuses an unknown Razorpay order")
        void refusesAnUnknownGatewayOrder() throws Exception {
            mockMvc.perform(post("/payments/verify")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.verifyPaymentRequest(
                                    "order_NOBODY", GATEWAY_PAYMENT_ID, "sig"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment not found for this Razorpay order"));
        }
    }

    // ==================================================================
    // the webhook
    // ==================================================================

    @Nested
    @DisplayName("POST /payments/webhook/razorpay")
    class Webhook {

        @BeforeEach
        void anInitiatedPayment() {
            storedPayment(TestDataFactory.USER_ID, PaymentStatus.INITIATED);
        }

        private void deliver(String payload, String signature, int expectedStatus) throws Exception {
            mockMvc.perform(post("/payments/webhook/razorpay")
                            .header(SIGNATURE_HEADER, signature)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().is(expectedStatus));
        }

        @Test
        @DisplayName("a genuine payment.captured marks the payment SUCCESS and tells order-service")
        void aGenuineCaptureIsApplied() throws Exception {
            String payload = TestDataFactory.paymentCapturedPayload();

            deliver(payload, TestDataFactory.webhookSignature(payload), 200);

            Payment stored = paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(stored.getGatewayPaymentId()).isEqualTo(GATEWAY_PAYMENT_ID);

            ArgumentCaptor<PaymentSuccessEvent> captor = ArgumentCaptor.forClass(PaymentSuccessEvent.class);
            verify(kafkaTemplate).send(eq("payment-success-topic"), captor.capture());
            assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
            assertThat(captor.getValue().getPaymentStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("a forged delivery changes nothing and publishes nothing")
        void aForgedDeliveryChangesNothing() throws Exception {
            deliver(TestDataFactory.paymentCapturedPayload(), "a-forged-signature", 500);

            assertThat(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.INITIATED);
            verify(kafkaTemplate, never()).send(any(String.class), any(PaymentSuccessEvent.class));
        }

        @Test
        @DisplayName("a delivery signed with the checkout secret is refused - the secrets differ")
        void aDeliverySignedWithTheWrongSecretIsRefused() throws Exception {
            String payload = TestDataFactory.paymentCapturedPayload();

            deliver(payload, TestDataFactory.hmacSha256Hex(payload, TestDataFactory.KEY_SECRET), 500);

            assertThat(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.INITIATED);
        }

        @Test
        @DisplayName("a genuine payment.failed records the gateway diagnosis")
        void aGenuineFailureIsRecorded() throws Exception {
            String payload = TestDataFactory.paymentFailedPayload();

            deliver(payload, TestDataFactory.webhookSignature(payload), 200);

            Payment stored = paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(stored.getGatewayErrorCode()).isEqualTo("BAD_REQUEST_ERROR");
            assertThat(stored.getFailureReason()).isEqualTo("payment_failed");
        }

        @Test
        @DisplayName("a redelivered capture is applied only once")
        void aRedeliveredCaptureIsAppliedOnce() throws Exception {
            String payload = TestDataFactory.paymentCapturedPayload();
            String signature = TestDataFactory.webhookSignature(payload);

            deliver(payload, signature, 200);
            deliver(payload, signature, 200);

            verify(kafkaTemplate).send(eq("payment-success-topic"), any(PaymentSuccessEvent.class));
        }

        @Test
        @DisplayName("an unrelated event is acknowledged and ignored")
        void anUnrelatedEventIsIgnored() throws Exception {
            String payload = TestDataFactory.eventPayload("subscription.charged");

            deliver(payload, TestDataFactory.webhookSignature(payload), 200);

            assertThat(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.INITIATED);
        }

        @Test
        @DisplayName("a refund.processed stores the refund and marks the payment REFUNDED")
        void aRefundIsStored() throws Exception {
            Payment captured = paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow();
            captured.setStatus(PaymentStatus.SUCCESS);
            captured.setGatewayPaymentId(GATEWAY_PAYMENT_ID);
            captured.setPaidAt(LocalDateTime.now());
            paymentRepository.save(captured);

            String payload = TestDataFactory.refundProcessedPayload();
            deliver(payload, TestDataFactory.webhookSignature(payload), 200);

            Payment stored = paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(stored.getRefundedAt()).isNotNull();

            assertThat(refundRepository.findAllByPaymentId(stored.getId()))
                    .singleElement()
                    .satisfies(refund -> {
                        assertThat(refund.getGatewayRefundId()).isEqualTo(TestDataFactory.GATEWAY_REFUND_ID);
                        assertThat(refund.getRefundAmount())
                                .isEqualByComparingTo(new BigDecimal("8999.00"));
                        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCESS);
                    });
        }

        @Test
        @DisplayName("without the signature header the delivery is a 400 and nothing changes")
        void withoutTheSignatureHeaderNothingChanges() throws Exception {
            mockMvc.perform(post("/payments/webhook/razorpay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(TestDataFactory.paymentCapturedPayload()))
                    .andExpect(status().isBadRequest());

            assertThat(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.INITIATED);
        }
    }

    // ==================================================================
    // refunds
    // ==================================================================

    @Nested
    @DisplayName("GET /payments/{paymentId}/refunds")
    class Refunds {

        private Payment payment;

        @BeforeEach
        void aRefundedPayment() {
            payment = storedPayment(TestDataFactory.USER_ID, PaymentStatus.REFUNDED);

            refundRepository.save(Refund.builder()
                    .payment(payment)
                    .refundAmount(new BigDecimal("1000.00"))
                    .status(RefundStatus.SUCCESS)
                    .gatewayRefundId("rfnd_A")
                    .reason("Customer changed their mind")
                    .refundedAt(LocalDateTime.now())
                    .build());
        }

        @Test
        @DisplayName("lists the refunds of the caller's own payment")
        void listsTheOwnRefunds() throws Exception {
            mockMvc.perform(get("/payments/{paymentId}/refunds", payment.getId())
                            .header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].refundAmount").value(1000.00))
                    .andExpect(jsonPath("$.data[0].gatewayRefundId").value("rfnd_A"));
        }

        @Test
        @DisplayName("never lists the refunds of another customer")
        void neverListsAnotherCustomersRefunds() throws Exception {
            mockMvc.perform(get("/payments/{paymentId}/refunds", payment.getId())
                            .header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment does not belong to this user"));
        }

        @Test
        @DisplayName("refuses an unknown payment")
        void refusesAnUnknownPayment() throws Exception {
            mockMvc.perform(get("/payments/{paymentId}/refunds", 999_999L)
                            .header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment not found"));
        }

        @Test
        @DisplayName("answers with an empty list when nothing was refunded")
        void answersWithAnEmptyList() throws Exception {
            refundRepository.deleteAll();

            mockMvc.perform(get("/payments/{paymentId}/refunds", payment.getId())
                            .header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ==================================================================
    // the helper page
    // ==================================================================

    @Nested
    @DisplayName("GET /payments/page/{gatewayOrderId}")
    class HelperPage {

        @Test
        @DisplayName("renders the checkout page of a stored payment")
        void rendersTheCheckoutPage() throws Exception {
            storedPayment(TestDataFactory.USER_ID, PaymentStatus.INITIATED);

            mockMvc.perform(get("/payments/page/{gatewayOrderId}", GATEWAY_ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(GATEWAY_ORDER_ID)))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            TestDataFactory.KEY_ID)))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "amount: " + TestDataFactory.AMOUNT_IN_PAISE)));
        }

        @Test
        @DisplayName("refuses a gateway order nobody initiated")
        void refusesAnUnknownGatewayOrder() throws Exception {
            mockMvc.perform(get("/payments/page/{gatewayOrderId}", "order_NOBODY"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Payment is not initiated yet"));
        }
    }

    // ==================================================================
    // resilience
    // ==================================================================

    @Nested
    @DisplayName("when order-service is down")
    class OrderServiceDown {

        @BeforeEach
        void orderServiceFails() {
            when(orderClient.getOrderById(anyLong()))
                    .thenThrow(new RuntimeException("connection refused"));
        }

        @Test
        @DisplayName("the caller is told, and no half started payment is left behind")
        void theCallerIsToldAndNothingIsStored() throws Exception {
            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));

            assertThat(paymentRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("the gateway is never called, no money is ever reserved blindly")
        void theGatewayIsNeverCalled() throws Exception {
            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isInternalServerError());

            verify(razorpayOrders, never()).create(any(JSONObject.class));
        }

        @Test
        @DisplayName("the circuit breaker of the order-service call records the failure")
        void theCircuitBreakerRecordsTheFailure() throws Exception {
            mockMvc.perform(post("/payments/initiate")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.initiatePaymentRequest())))
                    .andExpect(status().isInternalServerError());

            assertThat(circuitBreakerRegistry.circuitBreaker("order-service-call")
                    .getMetrics()
                    .getNumberOfFailedCalls())
                    .isPositive();
        }

        @Test
        @DisplayName("the webhook keeps working - it never talks to order-service")
        void theWebhookKeepsWorking() throws Exception {
            storedPayment(TestDataFactory.USER_ID, PaymentStatus.INITIATED);
            String payload = TestDataFactory.paymentCapturedPayload();

            mockMvc.perform(post("/payments/webhook/razorpay")
                            .header(SIGNATURE_HEADER, TestDataFactory.webhookSignature(payload))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk());

            assertThat(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS);
        }
    }

    // ==================================================================
    // the full happy path
    // ==================================================================

    @Test
    @DisplayName("the whole journey: initiate, verify and then the webhook confirms")
    void theWholeJourney() throws Exception {
        // 1. the customer starts the payment
        mockMvc.perform(post("/payments/initiate")
                        .header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.initiatePaymentRequest())))
                .andExpect(status().isCreated());

        assertThat(paymentRepository.findByOrderId(ORDER_ID).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.INITIATED);

        // 2. the browser comes back with the Razorpay signature
        mockMvc.perform(post("/payments/verify")
                        .header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.signedVerifyRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        // 3. Razorpay confirms out of band - the payment is already SUCCESS, so nothing changes
        String payload = TestDataFactory.paymentCapturedPayload();
        mockMvc.perform(post("/payments/webhook/razorpay")
                        .header(SIGNATURE_HEADER, TestDataFactory.webhookSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Payment stored = paymentRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(stored.getGatewayPaymentId()).isEqualTo(GATEWAY_PAYMENT_ID);
        assertThat(stored.getPaidAt()).isNotNull();

        // The /verify call already confirmed the order, so the webhook must not publish again.
        verify(kafkaTemplate, never()).send(any(String.class), any(PaymentSuccessEvent.class));
    }
}


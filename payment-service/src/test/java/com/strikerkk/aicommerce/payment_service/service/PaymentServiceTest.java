package com.strikerkk.aicommerce.payment_service.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.strikerkk.aicommerce.payment_service.auth.UserContext;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderResponse;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderStatus;
import com.strikerkk.aicommerce.payment_service.dto.request.InitiatePaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.request.VerifyPaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.response.InitiatePaymentResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.RefundResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.VerifyPaymentResponse;
import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.Refund;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import com.strikerkk.aicommerce.payment_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.payment_service.payment.VerifySignature;
import com.strikerkk.aicommerce.payment_service.repository.PaymentRepository;
import com.strikerkk.aicommerce.payment_service.repository.RefundRepository;
import com.strikerkk.aicommerce.payment_service.support.TestDataFactory;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService")
class PaymentServiceTest {

    private static final Long USER_ID = TestDataFactory.USER_ID;
    private static final Long ORDER_ID = TestDataFactory.ORDER_ID;
    private static final Long PAYMENT_ID = TestDataFactory.PAYMENT_ID;
    private static final String GATEWAY_ORDER_ID = TestDataFactory.GATEWAY_ORDER_ID;
    private static final String GATEWAY_PAYMENT_ID = TestDataFactory.GATEWAY_PAYMENT_ID;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentResilience4j paymentResilience4j;

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private com.razorpay.OrderClient razorpayOrders;

    @Mock
    private VerifySignature verifySignature;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // The gateway client exposes its sub clients as plain public fields.
        razorpayClient.orders = razorpayOrders;

        // The real ModelMapper is used on purpose: a mocked one would hide a broken mapping.
        paymentService = new PaymentService(
                paymentRepository,
                refundRepository,
                paymentResilience4j,
                razorpayClient,
                verifySignature,
                new ModelMapper());

        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", TestDataFactory.KEY_ID);

        // The gateway puts the caller id in a header, the interceptor puts it in the thread.
        UserContext.setUserId(String.valueOf(USER_ID));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private Payment savedPaymentWithId(Long id) {
        Payment payment = TestDataFactory.payment(id, USER_ID, PaymentStatus.INITIATED);
        payment.setGatewayOrderId(GATEWAY_ORDER_ID);
        return payment;
    }

    // ==================================================================
    // initiatePayment
    // ==================================================================

    @Nested
    @DisplayName("initiating a payment")
    class InitiatePayment {

        @BeforeEach
        void happyPath() throws Exception {
            when(paymentResilience4j.getOrderById(ORDER_ID)).thenReturn(TestDataFactory.orderResponse());
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(razorpayOrders.create(any(JSONObject.class))).thenReturn(TestDataFactory.razorpayOrder());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment payment = invocation.getArgument(0);
                payment.setId(PAYMENT_ID);
                return payment;
            });
        }

        @Test
        @DisplayName("asks order-service through the resilience guard, never the Feign client directly")
        void asksOrderServiceThroughTheGuard() {
            paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest());

            verify(paymentResilience4j).getOrderById(ORDER_ID);
        }

        @Test
        @DisplayName("stores an INITIATED RAZORPAY payment for the caller and the order")
        void storesAnInitiatedPayment() {
            paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest());

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());

            Payment saved = captor.getValue();
            assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getAmount()).isEqualByComparingTo(TestDataFactory.AMOUNT);
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.INITIATED);
            assertThat(saved.getGateway()).isEqualTo(PaymentGateway.RAZORPAY);
            assertThat(saved.getGatewayOrderId()).isEqualTo(GATEWAY_ORDER_ID);
        }

        @Test
        @DisplayName("charges what the order says is left to pay, converted to paise")
        void chargesTheAmountInPaise() throws Exception {
            paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest());

            ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
            verify(razorpayOrders).create(captor.capture());

            assertThat(captor.getValue().getInt("amount")).isEqualTo(TestDataFactory.AMOUNT_IN_PAISE);
        }

        @Test
        @DisplayName("always charges in INR and tags the receipt with the order id")
        void chargesInInrWithAReceipt() throws Exception {
            paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest());

            ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
            verify(razorpayOrders).create(captor.capture());

            assertThat(captor.getValue().getString("currency")).isEqualTo("INR");
            assertThat(captor.getValue().getString("receipt")).isEqualTo("order_" + ORDER_ID);
        }

        @Test
        @DisplayName("truncates the paise, a fractional paisa cannot be charged")
        void truncatesThePaise() throws Exception {
            when(paymentResilience4j.getOrderById(ORDER_ID)).thenReturn(
                    TestDataFactory.orderResponse(USER_ID, OrderStatus.PENDING, new BigDecimal("10.999")));

            paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest());

            ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
            verify(razorpayOrders).create(captor.capture());

            // 10.999 * 100 == 1099.9 -> intValue() drops the fraction.
            assertThat(captor.getValue().getInt("amount")).isEqualTo(1099);
        }

        @Test
        @DisplayName("hands the checkout everything it needs: id, amount, currency and the public key")
        void handsTheCheckoutEverythingItNeeds() {
            InitiatePaymentResponse response =
                    paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest());

            assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(response.getGatewayOrderId()).isEqualTo(GATEWAY_ORDER_ID);
            assertThat(response.getAmount()).isEqualByComparingTo(TestDataFactory.AMOUNT);
            assertThat(response.getCurrency()).isEqualTo("INR");
            assertThat(response.getGateway()).isEqualTo(PaymentGateway.RAZORPAY);
            assertThat(response.getKeyId()).isEqualTo(TestDataFactory.KEY_ID);
        }

        @Test
        @DisplayName("never leaks the key secret - only the publishable key id travels to the browser")
        void neverLeaksTheKeySecret() {
            InitiatePaymentResponse response =
                    paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest());

            assertThat(response.getKeyId()).doesNotContain(TestDataFactory.KEY_SECRET);
            assertThat(response.toString()).doesNotContain(TestDataFactory.KEY_SECRET);
        }

        @Test
        @DisplayName("refuses to pay somebody else's order")
        void refusesSomebodyElsesOrder() throws Exception {
            when(paymentResilience4j.getOrderById(ORDER_ID)).thenReturn(TestDataFactory.orderResponse(
                    TestDataFactory.OTHER_USER_ID, OrderStatus.PENDING, TestDataFactory.AMOUNT));

            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Order does not belong to this user");

            verify(razorpayOrders, never()).create(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses an order that is not PENDING any more")
        void refusesANonPendingOrder() throws Exception {
            when(paymentResilience4j.getOrderById(ORDER_ID)).thenReturn(TestDataFactory.orderResponse(
                    USER_ID, OrderStatus.CONFIRMED, TestDataFactory.AMOUNT));

            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Order is not in PENDING state, Cannot initiate payment");

            verify(razorpayOrders, never()).create(any());
        }

        @Test
        @DisplayName("refuses a cancelled order, the money would never be claimed")
        void refusesACancelledOrder() {
            when(paymentResilience4j.getOrderById(ORDER_ID)).thenReturn(TestDataFactory.orderResponse(
                    USER_ID, OrderStatus.CANCELLED, TestDataFactory.AMOUNT));

            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Order is not in PENDING state, Cannot initiate payment");
        }

        @Test
        @DisplayName("checks the ownership before the state, an intruder learns nothing about the order")
        void checksOwnershipFirst() {
            when(paymentResilience4j.getOrderById(ORDER_ID)).thenReturn(TestDataFactory.orderResponse(
                    TestDataFactory.OTHER_USER_ID, OrderStatus.DELIVERED, TestDataFactory.AMOUNT));

            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("refuses to start a second payment while one is still INITIATED")
        void refusesASecondInitiatedPayment() throws Exception {
            when(paymentRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment(1L, USER_ID, PaymentStatus.INITIATED)));

            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment already exists for this order");

            verify(razorpayOrders, never()).create(any());
        }

        @Test
        @DisplayName("refuses to charge an order twice once a payment succeeded")
        void refusesToChargeTwice() throws Exception {
            when(paymentRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment(1L, USER_ID, PaymentStatus.SUCCESS)));

            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment already exists for this order");

            verify(razorpayOrders, never()).create(any());
        }

        @Test
        @DisplayName("lets the customer retry after a FAILED attempt")
        void letsTheCustomerRetryAfterAFailure() {
            when(paymentRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment(1L, USER_ID, PaymentStatus.FAILED)));

            assertThat(paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest())).isNotNull();
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("lets the customer pay again after a REFUNDED attempt")
        void letsTheCustomerPayAgainAfterARefund() {
            when(paymentRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment(1L, USER_ID, PaymentStatus.REFUNDED)));

            assertThat(paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest())).isNotNull();
        }

        @Test
        @DisplayName("turns a gateway failure into a readable error and stores nothing")
        void turnsAGatewayFailureIntoAReadableError() throws Exception {
            when(razorpayOrders.create(any(JSONObject.class)))
                    .thenThrow(new RazorpayException("gateway is down"));

            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create Payment with Razorpay")
                    .hasMessageContaining("gateway is down");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("blows up when the gateway is called without a caller in the thread")
        void blowsUpWithoutACaller() {
            UserContext.clear();

            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(NumberFormatException.class);

            verifyNoInteractions(paymentResilience4j);
        }

        @Test
        @DisplayName("reads the caller from the thread, not from the request body")
        void readsTheCallerFromTheThread() {
            UserContext.setUserId(String.valueOf(TestDataFactory.OTHER_USER_ID));

            // The order belongs to USER_ID, the thread says OTHER_USER_ID - the call is refused.
            assertThatThrownBy(() -> paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest()))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("runs in a transaction, the gateway order and the row are committed together")
        void runsInATransaction() throws Exception {
            Method method = PaymentService.class.getMethod("initiatePayment", InitiatePaymentRequest.class);

            assertThat(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                    .isNotNull();
        }
    }

    // ==================================================================
    // verifyPayment
    // ==================================================================

    @Nested
    @DisplayName("verifying a payment")
    class VerifyPayment {

        private Payment stored;

        @BeforeEach
        void happyPath() {
            stored = savedPaymentWithId(PAYMENT_ID);

            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(stored));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(verifySignature.verifyRazorpaySignature(any(), any(), any())).thenReturn(true);
        }

        @Test
        @DisplayName("hands the three Razorpay fields to the signature check untouched")
        void handsTheThreeFieldsToTheCheck() {
            VerifyPaymentRequest request = TestDataFactory.signedVerifyRequest();

            paymentService.verifyPayment(request);

            verify(verifySignature).verifyRazorpaySignature(
                    request.getRazorpayOrderId(),
                    request.getRazorpayPaymentId(),
                    request.getRazorpaySignature());
        }

        @Test
        @DisplayName("marks the payment SUCCESS and stamps the moment it was paid")
        void marksThePaymentSuccess() {
            LocalDateTime before = LocalDateTime.now();

            paymentService.verifyPayment(TestDataFactory.signedVerifyRequest());

            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(stored.getPaidAt()).isNotNull().isAfterOrEqualTo(before);
            verify(paymentRepository).save(stored);
        }

        @Test
        @DisplayName("keeps the gateway payment id and the signature as the proof of payment")
        void keepsTheProofOfPayment() {
            VerifyPaymentRequest request = TestDataFactory.signedVerifyRequest();

            paymentService.verifyPayment(request);

            assertThat(stored.getGatewayPaymentId()).isEqualTo(request.getRazorpayPaymentId());
            assertThat(stored.getGatewaySignature()).isEqualTo(request.getRazorpaySignature());
        }

        @Test
        @DisplayName("answers with the payment, the order and the success message")
        void answersWithTheSuccess() {
            VerifyPaymentResponse response = paymentService.verifyPayment(TestDataFactory.signedVerifyRequest());

            assertThat(response.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(response.getMessage()).isEqualTo("Payment verified successfully");
            assertThat(response.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("marks the payment FAILED when the signature does not match")
        void marksThePaymentFailed() {
            when(verifySignature.verifyRazorpaySignature(any(), any(), any())).thenReturn(false);

            VerifyPaymentResponse response = paymentService.verifyPayment(
                    TestDataFactory.verifyPaymentRequest(GATEWAY_ORDER_ID, GATEWAY_PAYMENT_ID, "forged"));

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response.getMessage()).isEqualTo("Payment failed due to some reason");
            assertThat(stored.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentRepository).save(stored);
        }

        @Test
        @DisplayName("records why the verification failed, for the support desk")
        void recordsWhyItFailed() {
            when(verifySignature.verifyRazorpaySignature(any(), any(), any())).thenReturn(false);

            paymentService.verifyPayment(
                    TestDataFactory.verifyPaymentRequest(GATEWAY_ORDER_ID, GATEWAY_PAYMENT_ID, "forged"));

            assertThat(stored.getGatewayErrorCode()).isEqualTo("SIGNATURE_VERIFICATION_FAILED");
            assertThat(stored.getGatewayErrorMessage())
                    .isEqualTo("razorpay_signature does not match expected signature");
            assertThat(stored.getFailureReason()).isEqualTo("Payment signature failure failed");
            assertThat(stored.getFailedAt()).isNotNull();
        }

        @Test
        @DisplayName("never stores a forged signature")
        void neverStoresAForgedSignature() {
            when(verifySignature.verifyRazorpaySignature(any(), any(), any())).thenReturn(false);

            paymentService.verifyPayment(
                    TestDataFactory.verifyPaymentRequest(GATEWAY_ORDER_ID, GATEWAY_PAYMENT_ID, "forged"));

            assertThat(stored.getGatewaySignature()).isNull();
            assertThat(stored.getPaidAt()).isNull();
        }

        @Test
        @DisplayName("still keeps the gateway payment id of a failed attempt, to reconcile later")
        void keepsTheIdOfAFailedAttempt() {
            when(verifySignature.verifyRazorpaySignature(any(), any(), any())).thenReturn(false);

            paymentService.verifyPayment(
                    TestDataFactory.verifyPaymentRequest(GATEWAY_ORDER_ID, GATEWAY_PAYMENT_ID, "forged"));

            assertThat(stored.getGatewayPaymentId()).isEqualTo(GATEWAY_PAYMENT_ID);
        }

        @Test
        @DisplayName("refuses an unknown Razorpay order")
        void refusesAnUnknownOrder() {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.verifyPayment(TestDataFactory.signedVerifyRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment not found for this Razorpay order");

            verifyNoInteractions(verifySignature);
        }

        @Test
        @DisplayName("refuses to verify a payment of another user")
        void refusesAnotherUsersPayment() {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment(
                            PAYMENT_ID, TestDataFactory.OTHER_USER_ID, PaymentStatus.INITIATED)));

            assertThatThrownBy(() -> paymentService.verifyPayment(TestDataFactory.signedVerifyRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment does not belong to this user");

            verifyNoInteractions(verifySignature);
        }

        @Test
        @DisplayName("is idempotent by refusal: a payment already SUCCESS is never re-verified")
        void refusesToVerifyTwice() {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.capturedPayment()));

            assertThatThrownBy(() -> paymentService.verifyPayment(TestDataFactory.signedVerifyRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment already verified successfully");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("lets a FAILED payment be verified again, the customer may have retried")
        void letsAFailedPaymentBeVerifiedAgain() {
            Payment failed = TestDataFactory.payment(PAYMENT_ID, USER_ID, PaymentStatus.FAILED);
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(failed));

            VerifyPaymentResponse response = paymentService.verifyPayment(TestDataFactory.signedVerifyRequest());

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("blows up without a caller in the thread")
        void blowsUpWithoutACaller() {
            UserContext.clear();

            assertThatThrownBy(() -> paymentService.verifyPayment(TestDataFactory.signedVerifyRequest()))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("runs in a transaction")
        void runsInATransaction() throws Exception {
            Method method = PaymentService.class.getMethod("verifyPayment", VerifyPaymentRequest.class);

            assertThat(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                    .isNotNull();
        }
    }

    // ==================================================================
    // getRefundsByPaymentId
    // ==================================================================

    @Nested
    @DisplayName("listing the refunds of a payment")
    class GetRefunds {

        @BeforeEach
        void happyPath() {
            when(paymentRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(TestDataFactory.capturedPayment()));
        }

        @Test
        @DisplayName("maps every refund row of the payment")
        void mapsEveryRefundRow() {
            Payment payment = TestDataFactory.capturedPayment();
            when(refundRepository.findAllByPaymentId(PAYMENT_ID)).thenReturn(List.of(
                    TestDataFactory.refund(1L, payment, new BigDecimal("1000.00"), RefundStatus.SUCCESS),
                    TestDataFactory.refund(2L, payment, new BigDecimal("7999.00"), RefundStatus.PENDING)));

            List<RefundResponse> responses = paymentService.getRefundsByPaymentId(PAYMENT_ID);

            assertThat(responses).hasSize(2);
            assertThat(responses).extracting(RefundResponse::getId).containsExactly(1L, 2L);
            assertThat(responses).extracting(RefundResponse::getRefundAmount)
                    .containsExactly(new BigDecimal("1000.00"), new BigDecimal("7999.00"));
        }

        @Test
        @DisplayName("carries the gateway refund id and the reason through")
        void carriesTheGatewayFieldsThrough() {
            Refund refund = TestDataFactory.refund(1L, TestDataFactory.capturedPayment(),
                    TestDataFactory.AMOUNT, RefundStatus.SUCCESS);
            when(refundRepository.findAllByPaymentId(PAYMENT_ID)).thenReturn(List.of(refund));

            RefundResponse response = paymentService.getRefundsByPaymentId(PAYMENT_ID).getFirst();

            assertThat(response.getGatewayRefundId()).isEqualTo(TestDataFactory.GATEWAY_REFUND_ID);
            assertThat(response.getReason()).isEqualTo("Refund processed by razorpay");
            assertThat(response.getRefundedAt()).isNotNull();
        }

        @Test
        @DisplayName("answers with an empty list when nothing was ever refunded")
        void answersWithAnEmptyList() {
            when(refundRepository.findAllByPaymentId(PAYMENT_ID)).thenReturn(List.of());

            assertThat(paymentService.getRefundsByPaymentId(PAYMENT_ID)).isEmpty();
        }

        @Test
        @DisplayName("refuses an unknown payment")
        void refusesAnUnknownPayment() {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getRefundsByPaymentId(PAYMENT_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment not found");

            verifyNoInteractions(refundRepository);
        }

        @Test
        @DisplayName("refuses to show the refunds of another user")
        void refusesAnotherUsersRefunds() {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(
                    TestDataFactory.payment(PAYMENT_ID, TestDataFactory.OTHER_USER_ID, PaymentStatus.SUCCESS)));

            assertThatThrownBy(() -> paymentService.getRefundsByPaymentId(PAYMENT_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment does not belong to this user");

            verifyNoInteractions(refundRepository);
        }

        @Test
        @DisplayName("blows up without a caller in the thread")
        void blowsUpWithoutACaller() {
            UserContext.clear();

            assertThatThrownBy(() -> paymentService.getRefundsByPaymentId(PAYMENT_ID))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("is a read, so it is deliberately not transactional")
        void isNotTransactional() throws Exception {
            Method method = PaymentService.class.getMethod("getRefundsByPaymentId", Long.class);

            assertThat(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                    .isNull();
        }
    }

    // ==================================================================
    // wiring
    // ==================================================================

    @Nested
    @DisplayName("wiring")
    class Wiring {

        @Test
        @DisplayName("is a Spring service")
        void isASpringService() {
            assertThat(PaymentService.class.getAnnotation(org.springframework.stereotype.Service.class))
                    .isNotNull();
        }

        @Test
        @DisplayName("exposes exactly the three operations of the payment API")
        void exposesExactlyThreeOperations() {
            assertThat(PaymentService.class.getDeclaredMethods())
                    .filteredOn(m -> !m.isSynthetic())
                    .extracting(Method::getName)
                    .containsExactlyInAnyOrder("initiatePayment", "verifyPayment", "getRefundsByPaymentId");
        }
    }

    // ==================================================================
    // the gateway response
    // ==================================================================

    @Test
    @DisplayName("reads the gateway order id straight out of the Razorpay answer")
    void readsTheGatewayOrderIdFromTheAnswer() throws Exception {
        OrderResponse order = TestDataFactory.orderResponse();
        when(paymentResilience4j.getOrderById(ORDER_ID)).thenReturn(order);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(razorpayOrders.create(any(JSONObject.class)))
                .thenReturn(TestDataFactory.razorpayOrder("order_ANOTHERone"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        InitiatePaymentResponse response =
                paymentService.initiatePayment(TestDataFactory.initiatePaymentRequest());

        assertThat(response.getGatewayOrderId()).isEqualTo("order_ANOTHERone");
    }
}


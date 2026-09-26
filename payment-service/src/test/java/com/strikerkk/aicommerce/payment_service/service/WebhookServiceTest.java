package com.strikerkk.aicommerce.payment_service.service;

import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.Refund;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import com.strikerkk.aicommerce.payment_service.event.PaymentSuccessEvent;
import com.strikerkk.aicommerce.payment_service.payment.VerifySignature;
import com.strikerkk.aicommerce.payment_service.repository.PaymentRepository;
import com.strikerkk.aicommerce.payment_service.repository.RefundRepository;
import com.strikerkk.aicommerce.payment_service.support.TestDataFactory;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebhookService")
class WebhookServiceTest {

    private static final String TOPIC = "payment-success-topic";
    private static final String GATEWAY_ORDER_ID = TestDataFactory.GATEWAY_ORDER_ID;
    private static final String GATEWAY_PAYMENT_ID = TestDataFactory.GATEWAY_PAYMENT_ID;
    private static final String GATEWAY_REFUND_ID = TestDataFactory.GATEWAY_REFUND_ID;

    /** Razorpay signs the raw body, so the tests never care about the value itself. */
    private static final String ANY_SIGNATURE = "whatever-razorpay-sent";

    @Mock
    private VerifySignature verifySignature;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private KafkaTemplate<Long, PaymentSuccessEvent> paymentSuccessEventKafkaTemplate;

    @InjectMocks
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        when(verifySignature.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ==================================================================
    // the gate
    // ==================================================================

    @Nested
    @DisplayName("the signature gate")
    class SignatureGate {

        @Test
        @DisplayName("refuses a payload whose signature does not match, nobody can fake a payment")
        void refusesAForgedPayload() {
            when(verifySignature.verifyWebhookSignature(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook(
                    TestDataFactory.paymentCapturedPayload(), "forged"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid webhook signature");
        }

        @Test
        @DisplayName("never touches the database when the signature is wrong")
        void neverTouchesTheDatabase() {
            when(verifySignature.verifyWebhookSignature(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook(
                    TestDataFactory.paymentCapturedPayload(), "forged"))
                    .isInstanceOf(RuntimeException.class);

            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(refundRepository);
            verifyNoInteractions(paymentSuccessEventKafkaTemplate);
        }

        @Test
        @DisplayName("checks the signature against the exact raw body Razorpay posted")
        void checksTheRawBody() {
            String payload = TestDataFactory.paymentCapturedPayload();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment()));

            webhookService.handleRazorpayWebhook(payload, ANY_SIGNATURE);

            verify(verifySignature).verifyWebhookSignature(payload, ANY_SIGNATURE);
        }

        @Test
        @DisplayName("checks the signature before it even parses the body")
        void checksTheSignatureBeforeParsing() {
            when(verifySignature.verifyWebhookSignature(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook("this is not json", "forged"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid webhook signature");
        }
    }

    // ==================================================================
    // payment.captured
    // ==================================================================

    @Nested
    @DisplayName("payment.captured")
    class PaymentCaptured {

        @Test
        @DisplayName("promotes an INITIATED payment to SUCCESS and stamps the moment")
        void promotesAnInitiatedPayment() {
            Payment payment = TestDataFactory.payment();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));
            LocalDateTime before = LocalDateTime.now();

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentCapturedPayload(), ANY_SIGNATURE);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getGatewayPaymentId()).isEqualTo(GATEWAY_PAYMENT_ID);
            assertThat(payment.getPaidAt()).isNotNull().isAfterOrEqualTo(before);
            verify(paymentRepository).save(payment);
        }

        @Test
        @DisplayName("looks the payment up by the gateway order id carried in the event")
        void looksThePaymentUpByGatewayOrderId() {
            when(paymentRepository.findByGatewayOrderId("order_OTHER"))
                    .thenReturn(Optional.of(TestDataFactory.payment()));

            webhookService.handleRazorpayWebhook(
                    TestDataFactory.paymentCapturedPayload(GATEWAY_PAYMENT_ID, "order_OTHER"), ANY_SIGNATURE);

            verify(paymentRepository).findByGatewayOrderId("order_OTHER");
        }

        @Test
        @DisplayName("tells order-service to confirm the order over Kafka")
        void tellsOrderServiceToConfirm() {
            Payment payment = TestDataFactory.payment();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentCapturedPayload(), ANY_SIGNATURE);

            ArgumentCaptor<PaymentSuccessEvent> captor = ArgumentCaptor.forClass(PaymentSuccessEvent.class);
            verify(paymentSuccessEventKafkaTemplate).send(eq(TOPIC), captor.capture());

            PaymentSuccessEvent event = captor.getValue();
            assertThat(event.getId()).isEqualTo(TestDataFactory.PAYMENT_ID);
            assertThat(event.getUserId()).isEqualTo(TestDataFactory.USER_ID);
            assertThat(event.getOrderId()).isEqualTo(TestDataFactory.ORDER_ID);
            assertThat(event.getPaymentStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("publishes the event only after the row has been stored")
        void publishesAfterStoring() {
            Payment payment = TestDataFactory.payment();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentCapturedPayload(), ANY_SIGNATURE);

            org.mockito.InOrder inOrder =
                    org.mockito.Mockito.inOrder(paymentRepository, paymentSuccessEventKafkaTemplate);
            inOrder.verify(paymentRepository).save(payment);
            inOrder.verify(paymentSuccessEventKafkaTemplate).send(eq(TOPIC), any(PaymentSuccessEvent.class));
        }

        @Test
        @DisplayName("rescues a payment that /verify had marked FAILED - the gateway has the last word")
        void rescuesAFailedPayment() {
            Payment payment = TestDataFactory.payment(
                    TestDataFactory.PAYMENT_ID, TestDataFactory.USER_ID, PaymentStatus.FAILED);
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentCapturedPayload(), ANY_SIGNATURE);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(paymentSuccessEventKafkaTemplate).send(eq(TOPIC), any(PaymentSuccessEvent.class));
        }

        @Test
        @DisplayName("is idempotent: a payment already SUCCESS is left alone and no event is re-published")
        void isIdempotent() {
            Payment payment = TestDataFactory.capturedPayment();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentCapturedPayload(), ANY_SIGNATURE);

            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(paymentSuccessEventKafkaTemplate);
        }

        @Test
        @DisplayName("never resurrects a REFUNDED payment")
        void neverResurrectsARefundedPayment() {
            Payment payment = TestDataFactory.payment(
                    TestDataFactory.PAYMENT_ID, TestDataFactory.USER_ID, PaymentStatus.REFUNDED);
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentCapturedPayload(), ANY_SIGNATURE);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(paymentRepository, never()).save(any());
            verifyNoInteractions(paymentSuccessEventKafkaTemplate);
        }

        @Test
        @DisplayName("fails loudly on an unknown gateway order, so Razorpay retries")
        void failsLoudlyOnAnUnknownOrder() {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook(
                    TestDataFactory.paymentCapturedPayload(), ANY_SIGNATURE))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment not found");
        }
    }

    // ==================================================================
    // payment.failed
    // ==================================================================

    @Nested
    @DisplayName("payment.failed")
    class PaymentFailed {

        @Test
        @DisplayName("marks an INITIATED payment FAILED and stamps the moment")
        void marksAnInitiatedPaymentFailed() {
            Payment payment = TestDataFactory.payment();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));
            LocalDateTime before = LocalDateTime.now();

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentFailedPayload(), ANY_SIGNATURE);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailedAt()).isNotNull().isAfterOrEqualTo(before);
            verify(paymentRepository).save(payment);
        }

        @Test
        @DisplayName("keeps the gateway diagnosis, so support can answer 'why was I declined?'")
        void keepsTheGatewayDiagnosis() {
            Payment payment = TestDataFactory.payment();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentFailedPayload(), ANY_SIGNATURE);

            assertThat(payment.getGatewayPaymentId()).isEqualTo(GATEWAY_PAYMENT_ID);
            assertThat(payment.getGatewayErrorCode()).isEqualTo("BAD_REQUEST_ERROR");
            assertThat(payment.getGatewayErrorMessage()).isEqualTo("Payment was declined by the bank");
            assertThat(payment.getFailureReason()).isEqualTo("payment_failed");
        }

        @Test
        @DisplayName("copes with an event that carries no error details at all")
        void copesWithoutErrorDetails() {
            Payment payment = TestDataFactory.payment();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(
                    TestDataFactory.paymentFailedPayload(GATEWAY_PAYMENT_ID, GATEWAY_ORDER_ID, null, null, null),
                    ANY_SIGNATURE);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getGatewayErrorCode()).isNull();
            assertThat(payment.getGatewayErrorMessage()).isNull();
            assertThat(payment.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("never downgrades a payment that already succeeded")
        void neverDowngradesASuccess() {
            Payment payment = TestDataFactory.capturedPayment();
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentFailedPayload(), ANY_SIGNATURE);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("ignores a repeated failure, the row is already FAILED")
        void ignoresARepeatedFailure() {
            Payment payment = TestDataFactory.payment(
                    TestDataFactory.PAYMENT_ID, TestDataFactory.USER_ID, PaymentStatus.FAILED);
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentFailedPayload(), ANY_SIGNATURE);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("never publishes a success event")
        void neverPublishesASuccessEvent() {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment()));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentFailedPayload(), ANY_SIGNATURE);

            verifyNoInteractions(paymentSuccessEventKafkaTemplate);
        }

        @Test
        @DisplayName("fails loudly on an unknown gateway order")
        void failsLoudlyOnAnUnknownOrder() {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook(
                    TestDataFactory.paymentFailedPayload(), ANY_SIGNATURE))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment not found");
        }
    }

    // ==================================================================
    // refund.processed
    // ==================================================================

    @Nested
    @DisplayName("refund.processed")
    class RefundProcessed {

        @Test
        @DisplayName("stores the refund in rupees, the event counts in paise")
        void storesTheRefundInRupees() {
            Payment payment = TestDataFactory.capturedPayment();
            when(paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.refundProcessedPayload(), ANY_SIGNATURE);

            ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(captor.capture());

            Refund refund = captor.getValue();
            assertThat(refund.getRefundAmount()).isEqualByComparingTo(new BigDecimal("8999.00"));
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCESS);
            assertThat(refund.getGatewayRefundId()).isEqualTo(GATEWAY_REFUND_ID);
            assertThat(refund.getPayment()).isSameAs(payment);
            assertThat(refund.getRefundedAt()).isNotNull();
        }

        @Test
        @DisplayName("handles a partial refund")
        void handlesAPartialRefund() {
            Payment payment = TestDataFactory.capturedPayment();
            when(paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(
                    TestDataFactory.refundProcessedPayload(GATEWAY_REFUND_ID, GATEWAY_PAYMENT_ID, 50000),
                    ANY_SIGNATURE);

            ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(captor.capture());

            assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo(new BigDecimal("500"));
        }

        @Test
        @DisplayName("marks the payment REFUNDED and stamps the moment")
        void marksThePaymentRefunded() {
            Payment payment = TestDataFactory.capturedPayment();
            when(paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID)).thenReturn(Optional.of(payment));
            LocalDateTime before = LocalDateTime.now();

            webhookService.handleRazorpayWebhook(TestDataFactory.refundProcessedPayload(), ANY_SIGNATURE);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(payment.getRefundedAt()).isNotNull().isAfterOrEqualTo(before);
            verify(paymentRepository).save(payment);
        }

        @Test
        @DisplayName("looks the payment up by the gateway payment id, a refund knows no order id")
        void looksThePaymentUpByGatewayPaymentId() {
            when(paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID))
                    .thenReturn(Optional.of(TestDataFactory.capturedPayment()));

            webhookService.handleRazorpayWebhook(TestDataFactory.refundProcessedPayload(), ANY_SIGNATURE);

            verify(paymentRepository).findByGatewayPaymentId(GATEWAY_PAYMENT_ID);
        }

        @Test
        @DisplayName("is idempotent: the same refund id is never stored twice")
        void isIdempotent() {
            Payment payment = TestDataFactory.capturedPayment();
            payment.getRefunds().add(TestDataFactory.refund(
                    1L, payment, TestDataFactory.AMOUNT, RefundStatus.SUCCESS));
            when(paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.refundProcessedPayload(), ANY_SIGNATURE);

            verifyNoInteractions(refundRepository);
            verify(paymentRepository, never()).save(any());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("a different refund of the same payment is still stored")
        void storesASecondDifferentRefund() {
            Payment payment = TestDataFactory.capturedPayment();
            Refund earlier = TestDataFactory.refund(
                    1L, payment, new BigDecimal("100.00"), RefundStatus.SUCCESS);
            earlier.setGatewayRefundId("rfnd_ANOTHERone");
            payment.getRefunds().add(earlier);
            when(paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID)).thenReturn(Optional.of(payment));

            webhookService.handleRazorpayWebhook(TestDataFactory.refundProcessedPayload(), ANY_SIGNATURE);

            verify(refundRepository).save(any(Refund.class));
        }

        @Test
        @DisplayName("fails loudly on an unknown gateway payment")
        void failsLoudlyOnAnUnknownPayment() {
            when(paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook(
                    TestDataFactory.refundProcessedPayload(), ANY_SIGNATURE))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Payment not found");
        }

        @Test
        @DisplayName("never publishes a success event")
        void neverPublishesASuccessEvent() {
            when(paymentRepository.findByGatewayPaymentId(GATEWAY_PAYMENT_ID))
                    .thenReturn(Optional.of(TestDataFactory.capturedPayment()));

            webhookService.handleRazorpayWebhook(TestDataFactory.refundProcessedPayload(), ANY_SIGNATURE);

            verifyNoInteractions(paymentSuccessEventKafkaTemplate);
        }

        @Test
        @DisplayName("cannot read the payload Razorpay really sends (documents the bug)")
        void cannotReadTheRealRazorpayPayload() {
            // The handler reads payload.payment.entity, but Razorpay puts the refund fields
            // under payload.refund.entity. A real refund.processed event therefore blows up on
            // the missing "payment_id" key and Razorpay keeps retrying the delivery.
            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook(
                    TestDataFactory.razorpayShapedRefundProcessedPayload(), ANY_SIGNATURE))
                    .isInstanceOf(JSONException.class)
                    .hasMessageContaining("payment_id");

            verifyNoInteractions(refundRepository);
        }

        @Test
        @DisplayName("the @Transactional on the private handler is inert (documents the bug)")
        void theTransactionalOnThePrivateHandlerIsInert() throws Exception {
            java.lang.reflect.Method handler =
                    WebhookService.class.getDeclaredMethod("handleRefundProcessed", org.json.JSONObject.class);

            // Spring proxies only public methods called from the outside, so the refund row and the
            // payment update are committed independently instead of atomically.
            assertThat(java.lang.reflect.Modifier.isPrivate(handler.getModifiers())).isTrue();
            assertThat(handler.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                    .isNotNull();
            assertThat(WebhookService.class.getMethod("handleRazorpayWebhook", String.class, String.class)
                    .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                    .isNull();
        }
    }

    // ==================================================================
    // routing
    // ==================================================================

    @Nested
    @DisplayName("routing")
    class Routing {

        @Test
        @DisplayName("ignores an event it does not care about")
        void ignoresAnUnknownEvent() {
            webhookService.handleRazorpayWebhook(TestDataFactory.eventPayload("order.paid"), ANY_SIGNATURE);

            verifyNoInteractions(paymentRepository);
            verifyNoInteractions(refundRepository);
            verifyNoInteractions(paymentSuccessEventKafkaTemplate);
        }

        @Test
        @DisplayName("ignores the subscription and payout traffic of a shared webhook endpoint")
        void ignoresUnrelatedTraffic() {
            webhookService.handleRazorpayWebhook(TestDataFactory.eventPayload("subscription.charged"), ANY_SIGNATURE);
            webhookService.handleRazorpayWebhook(TestDataFactory.eventPayload("payout.processed"), ANY_SIGNATURE);
            webhookService.handleRazorpayWebhook(TestDataFactory.eventPayload("payment.authorized"), ANY_SIGNATURE);

            verifyNoInteractions(paymentRepository);
        }

        @Test
        @DisplayName("the routing is case sensitive - 'Payment.Captured' is not 'payment.captured'")
        void theRoutingIsCaseSensitive() {
            webhookService.handleRazorpayWebhook(TestDataFactory.eventPayload("Payment.Captured"), ANY_SIGNATURE);

            verifyNoInteractions(paymentRepository);
        }

        @Test
        @DisplayName("rejects a body that is not JSON")
        void rejectsANonJsonBody() {
            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook("not json at all", ANY_SIGNATURE))
                    .isInstanceOf(JSONException.class);
        }

        @Test
        @DisplayName("rejects a JSON body without an 'event'")
        void rejectsABodyWithoutAnEvent() {
            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook("{\"payload\":{}}", ANY_SIGNATURE))
                    .isInstanceOf(JSONException.class)
                    .hasMessageContaining("event");
        }

        @Test
        @DisplayName("rejects a known event without a payload")
        void rejectsAKnownEventWithoutAPayload() {
            assertThatThrownBy(() -> webhookService.handleRazorpayWebhook(
                    TestDataFactory.eventPayload("payment.captured"), ANY_SIGNATURE))
                    .isInstanceOf(JSONException.class);
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
            assertThat(WebhookService.class.getAnnotation(org.springframework.stereotype.Service.class))
                    .isNotNull();
        }

        @Test
        @DisplayName("exposes a single entry point, the three handlers stay private")
        void exposesASingleEntryPoint() {
            assertThat(WebhookService.class.getDeclaredMethods())
                    .filteredOn(m -> !m.isSynthetic() && java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                    .extracting(java.lang.reflect.Method::getName)
                    .containsExactly("handleRazorpayWebhook");
        }

        @Test
        @DisplayName("publishes on the topic order-service listens on")
        void publishesOnTheAgreedTopic() {
            when(paymentRepository.findByGatewayOrderId(GATEWAY_ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.payment()));

            webhookService.handleRazorpayWebhook(TestDataFactory.paymentCapturedPayload(), ANY_SIGNATURE);

            verify(paymentSuccessEventKafkaTemplate).send(eq(TOPIC), any(PaymentSuccessEvent.class));
        }
    }
}


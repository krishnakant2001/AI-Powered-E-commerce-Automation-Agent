package com.strikerkk.aicommerce.payment_service.repository;

import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.Refund;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.hibernate.exception.ConstraintViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("PaymentRepository")
class PaymentRepositoryTest {

    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 100L;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Payment payment(Long orderId, Long userId, PaymentStatus status, String gatewayOrderId) {
        return Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(new BigDecimal("8999.00"))
                .status(status)
                .gateway(PaymentGateway.RAZORPAY)
                .gatewayOrderId(gatewayOrderId)
                .refunds(new ArrayList<>())
                .build();
    }

    private Payment persisted(Long orderId, Long userId, PaymentStatus status, String gatewayOrderId) {
        return entityManager.persistFlushFind(payment(orderId, userId, status, gatewayOrderId));
    }

    // ==================================================================
    // findByOrderId
    // ==================================================================

    @Nested
    @DisplayName("findByOrderId")
    class FindByOrderId {

        @Test
        @DisplayName("finds the payment attempt of an order, the guard against a double charge")
        void findsThePaymentOfAnOrder() {
            persisted(ORDER_ID, USER_ID, PaymentStatus.INITIATED, "order_A");

            Optional<Payment> found = paymentRepository.findByOrderId(ORDER_ID);

            assertThat(found).isPresent();
            assertThat(found.get().getOrderId()).isEqualTo(ORDER_ID);
            assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.INITIATED);
        }

        @Test
        @DisplayName("answers empty for an order nobody tried to pay yet")
        void answersEmptyForAnUnpaidOrder() {
            assertThat(paymentRepository.findByOrderId(999L)).isEmpty();
        }

        @Test
        @DisplayName("never mixes two orders up")
        void neverMixesTwoOrdersUp() {
            persisted(ORDER_ID, USER_ID, PaymentStatus.SUCCESS, "order_A");
            persisted(101L, USER_ID, PaymentStatus.INITIATED, "order_B");

            assertThat(paymentRepository.findByOrderId(ORDER_ID).orElseThrow().getGatewayOrderId())
                    .isEqualTo("order_A");
            assertThat(paymentRepository.findByOrderId(101L).orElseThrow().getGatewayOrderId())
                    .isEqualTo("order_B");
        }

        @Test
        @DisplayName("answers empty for a null order id instead of blowing up")
        void answersEmptyForANullOrderId() {
            assertThat(paymentRepository.findByOrderId(null)).isEmpty();
        }
    }

    // ==================================================================
    // findByGatewayOrderId
    // ==================================================================

    @Nested
    @DisplayName("findByGatewayOrderId")
    class FindByGatewayOrderId {

        @Test
        @DisplayName("finds the payment the checkout callback refers to")
        void findsThePaymentOfACallback() {
            persisted(ORDER_ID, USER_ID, PaymentStatus.INITIATED, "order_RAZORPAYid");

            Optional<Payment> found = paymentRepository.findByGatewayOrderId("order_RAZORPAYid");

            assertThat(found).isPresent();
            assertThat(found.get().getOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("answers empty for a gateway order nobody knows")
        void answersEmptyForAnUnknownGatewayOrder() {
            assertThat(paymentRepository.findByGatewayOrderId("order_NOBODY")).isEmpty();
        }

        @Test
        @DisplayName("is case sensitive, Razorpay ids are")
        void isCaseSensitive() {
            persisted(ORDER_ID, USER_ID, PaymentStatus.INITIATED, "order_AbCdEf");

            assertThat(paymentRepository.findByGatewayOrderId("order_abcdef")).isEmpty();
            assertThat(paymentRepository.findByGatewayOrderId("order_AbCdEf")).isPresent();
        }

        @Test
        @DisplayName("refuses to store the same gateway order id twice")
        void refusesADuplicateGatewayOrderId() {
            persisted(ORDER_ID, USER_ID, PaymentStatus.INITIATED, "order_DUPLICATE");

            assertThatThrownBy(() -> {
                entityManager.persist(payment(101L, USER_ID, PaymentStatus.INITIATED, "order_DUPLICATE"));
                entityManager.flush();
            })
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("GATEWAY_ORDER_ID");
        }

        @Test
        @DisplayName("tolerates several payments that never reached the gateway")
        void toleratesSeveralNullGatewayOrderIds() {
            persisted(ORDER_ID, USER_ID, PaymentStatus.FAILED, null);
            persisted(101L, USER_ID, PaymentStatus.FAILED, null);

            assertThat(paymentRepository.findAll()).hasSize(2);
        }
    }

    // ==================================================================
    // findByGatewayPaymentId
    // ==================================================================

    @Nested
    @DisplayName("findByGatewayPaymentId")
    class FindByGatewayPaymentId {

        @Test
        @DisplayName("finds the payment a refund webhook refers to")
        void findsThePaymentOfARefund() {
            Payment payment = payment(ORDER_ID, USER_ID, PaymentStatus.SUCCESS, "order_A");
            payment.setGatewayPaymentId("pay_RAZORPAYid");
            entityManager.persistAndFlush(payment);

            Optional<Payment> found = paymentRepository.findByGatewayPaymentId("pay_RAZORPAYid");

            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("answers empty while the payment has not been captured yet")
        void answersEmptyBeforeCapture() {
            persisted(ORDER_ID, USER_ID, PaymentStatus.INITIATED, "order_A");

            assertThat(paymentRepository.findByGatewayPaymentId("pay_ANY")).isEmpty();
        }

        @Test
        @DisplayName("refuses to store the same gateway payment id twice")
        void refusesADuplicateGatewayPaymentId() {
            Payment first = payment(ORDER_ID, USER_ID, PaymentStatus.SUCCESS, "order_A");
            first.setGatewayPaymentId("pay_DUPLICATE");
            entityManager.persistAndFlush(first);

            assertThatThrownBy(() -> {
                Payment second = payment(101L, USER_ID, PaymentStatus.SUCCESS, "order_B");
                second.setGatewayPaymentId("pay_DUPLICATE");
                entityManager.persist(second);
                entityManager.flush();
            })
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("GATEWAY_PAYMENT_ID");
        }
    }

    // ==================================================================
    // the row itself
    // ==================================================================

    @Nested
    @DisplayName("the stored row")
    class StoredRow {

        @Test
        @DisplayName("gets an identity and both timestamps on insert")
        void getsAnIdentityAndTimestamps() {
            Payment stored = persisted(ORDER_ID, USER_ID, PaymentStatus.INITIATED, "order_A");

            assertThat(stored.getId()).isNotNull();
            assertThat(stored.getCreatedAt()).isNotNull();
            assertThat(stored.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("keeps the amount with two decimals, money is never rounded away")
        void keepsTwoDecimals() {
            Payment payment = payment(ORDER_ID, USER_ID, PaymentStatus.INITIATED, "order_A");
            payment.setAmount(new BigDecimal("1234.56"));

            Payment stored = entityManager.persistFlushFind(payment);
            entityManager.clear();

            assertThat(paymentRepository.findById(stored.getId()).orElseThrow().getAmount())
                    .isEqualByComparingTo(new BigDecimal("1234.56"));
        }

        @Test
        @DisplayName("stores the status and the gateway as readable text, not as an ordinal")
        void storesEnumsAsText() {
            Payment stored = persisted(ORDER_ID, USER_ID, PaymentStatus.REFUNDED, "order_A");
            entityManager.clear();

            Object status = entityManager.getEntityManager()
                    .createNativeQuery("select status from payment where id = " + stored.getId())
                    .getSingleResult();
            Object gateway = entityManager.getEntityManager()
                    .createNativeQuery("select gateway from payment where id = " + stored.getId())
                    .getSingleResult();

            assertThat(status).isEqualTo("REFUNDED");
            assertThat(gateway).isEqualTo("RAZORPAY");
        }

        @Test
        @DisplayName("refuses a payment without an order")
        void refusesAPaymentWithoutAnOrder() {
            assertThatThrownBy(() -> {
                entityManager.persist(payment(null, USER_ID, PaymentStatus.INITIATED, "order_A"));
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("refuses a payment without a user")
        void refusesAPaymentWithoutAUser() {
            assertThatThrownBy(() -> {
                entityManager.persist(payment(ORDER_ID, null, PaymentStatus.INITIATED, "order_A"));
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("refuses a payment without an amount")
        void refusesAPaymentWithoutAnAmount() {
            Payment payment = payment(ORDER_ID, USER_ID, PaymentStatus.INITIATED, "order_A");
            payment.setAmount(null);

            assertThatThrownBy(() -> {
                entityManager.persist(payment);
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("refuses a payment without a status")
        void refusesAPaymentWithoutAStatus() {
            Payment payment = payment(ORDER_ID, USER_ID, null, "order_A");

            assertThatThrownBy(() -> {
                entityManager.persist(payment);
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("keeps the gateway diagnosis of a failed attempt")
        void keepsTheGatewayDiagnosis() {
            Payment payment = payment(ORDER_ID, USER_ID, PaymentStatus.FAILED, "order_A");
            payment.setGatewayErrorCode("BAD_REQUEST_ERROR");
            payment.setGatewayErrorMessage("Payment was declined by the bank");
            payment.setFailureReason("payment_failed");
            payment.setFailedAt(LocalDateTime.now());

            Payment stored = entityManager.persistFlushFind(payment);
            entityManager.clear();

            Payment reloaded = paymentRepository.findById(stored.getId()).orElseThrow();
            assertThat(reloaded.getGatewayErrorCode()).isEqualTo("BAD_REQUEST_ERROR");
            assertThat(reloaded.getGatewayErrorMessage()).isEqualTo("Payment was declined by the bank");
            assertThat(reloaded.getFailureReason()).isEqualTo("payment_failed");
            assertThat(reloaded.getFailedAt()).isNotNull();
        }

        @Test
        @DisplayName("keeps the proof of a successful payment")
        void keepsTheProofOfPayment() {
            Payment payment = payment(ORDER_ID, USER_ID, PaymentStatus.SUCCESS, "order_A");
            payment.setGatewayPaymentId("pay_A");
            payment.setGatewaySignature("a-signature");
            payment.setPaidAt(LocalDateTime.now());

            Payment stored = entityManager.persistFlushFind(payment);
            entityManager.clear();

            Payment reloaded = paymentRepository.findById(stored.getId()).orElseThrow();
            assertThat(reloaded.getGatewayPaymentId()).isEqualTo("pay_A");
            assertThat(reloaded.getGatewaySignature()).isEqualTo("a-signature");
            assertThat(reloaded.getPaidAt()).isNotNull();
        }
    }

    // ==================================================================
    // the refunds
    // ==================================================================

    @Nested
    @DisplayName("the refunds of a payment")
    class Refunds {

        private Refund refund(Payment payment, String gatewayRefundId, String amount) {
            return Refund.builder()
                    .payment(payment)
                    .refundAmount(new BigDecimal(amount))
                    .status(RefundStatus.SUCCESS)
                    .gatewayRefundId(gatewayRefundId)
                    .reason("Refund processed by razorpay")
                    .refundedAt(LocalDateTime.now())
                    .build();
        }

        @Test
        @DisplayName("start out empty, the builder never leaves the list null")
        void startOutEmpty() {
            Payment stored = persisted(ORDER_ID, USER_ID, PaymentStatus.SUCCESS, "order_A");

            assertThat(stored.getRefunds()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("are cascaded when the payment is stored")
        void areCascaded() {
            Payment payment = payment(ORDER_ID, USER_ID, PaymentStatus.REFUNDED, "order_A");
            payment.getRefunds().add(refund(payment, "rfnd_A", "1000.00"));
            payment.getRefunds().add(refund(payment, "rfnd_B", "7999.00"));

            Payment stored = entityManager.persistFlushFind(payment);
            entityManager.clear();

            assertThat(paymentRepository.findById(stored.getId()).orElseThrow().getRefunds()).hasSize(2);
        }

        @Test
        @DisplayName("are deleted with the payment, a refund cannot outlive it")
        void areDeletedWithThePayment() {
            Payment payment = payment(ORDER_ID, USER_ID, PaymentStatus.REFUNDED, "order_A");
            payment.getRefunds().add(refund(payment, "rfnd_A", "1000.00"));
            Payment stored = entityManager.persistFlushFind(payment);

            paymentRepository.delete(stored);
            entityManager.flush();
            entityManager.clear();

            Long remaining = ((Number) entityManager.getEntityManager()
                    .createNativeQuery("select count(*) from refunds")
                    .getSingleResult()).longValue();
            assertThat(remaining).isZero();
        }

        @Test
        @DisplayName("are removed from the table when they are removed from the list")
        void areOrphanRemoved() {
            Payment payment = payment(ORDER_ID, USER_ID, PaymentStatus.REFUNDED, "order_A");
            payment.getRefunds().add(refund(payment, "rfnd_A", "1000.00"));
            Payment stored = entityManager.persistFlushFind(payment);

            stored.getRefunds().clear();
            entityManager.flush();
            entityManager.clear();

            Long remaining = ((Number) entityManager.getEntityManager()
                    .createNativeQuery("select count(*) from refunds")
                    .getSingleResult()).longValue();
            assertThat(remaining).isZero();
        }
    }
}





package com.strikerkk.aicommerce.payment_service.repository;

import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.Refund;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("RefundRepository")
class RefundRepositoryTest {

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Payment payment;
    private Payment otherPayment;

    @BeforeEach
    void setUp() {
        payment = entityManager.persistFlushFind(payment(100L, "order_A", "pay_A"));
        otherPayment = entityManager.persistFlushFind(payment(101L, "order_B", "pay_B"));
    }

    private Payment payment(Long orderId, String gatewayOrderId, String gatewayPaymentId) {
        Payment built = Payment.builder()
                .orderId(orderId)
                .userId(42L)
                .amount(new BigDecimal("8999.00"))
                .status(PaymentStatus.SUCCESS)
                .gateway(PaymentGateway.RAZORPAY)
                .gatewayOrderId(gatewayOrderId)
                .refunds(new ArrayList<>())
                .build();
        built.setGatewayPaymentId(gatewayPaymentId);
        return built;
    }

    private Refund refund(Payment owner, String gatewayRefundId, String amount, RefundStatus status) {
        return Refund.builder()
                .payment(owner)
                .refundAmount(new BigDecimal(amount))
                .status(status)
                .gatewayRefundId(gatewayRefundId)
                .reason("Refund processed by razorpay")
                .refundedAt(LocalDateTime.now())
                .build();
    }

    // ==================================================================
    // findAllByPaymentId
    // ==================================================================

    @Nested
    @DisplayName("findAllByPaymentId")
    class FindAllByPaymentId {

        @Test
        @DisplayName("finds every refund of the payment")
        void findsEveryRefund() {
            entityManager.persistAndFlush(refund(payment, "rfnd_A", "1000.00", RefundStatus.SUCCESS));
            entityManager.persistAndFlush(refund(payment, "rfnd_B", "7999.00", RefundStatus.PENDING));

            List<Refund> found = refundRepository.findAllByPaymentId(payment.getId());

            assertThat(found).hasSize(2);
            assertThat(found).extracting(Refund::getGatewayRefundId)
                    .containsExactlyInAnyOrder("rfnd_A", "rfnd_B");
        }

        @Test
        @DisplayName("never leaks the refunds of another payment")
        void neverLeaksAnotherPaymentsRefunds() {
            entityManager.persistAndFlush(refund(payment, "rfnd_A", "1000.00", RefundStatus.SUCCESS));
            entityManager.persistAndFlush(refund(otherPayment, "rfnd_B", "500.00", RefundStatus.SUCCESS));

            assertThat(refundRepository.findAllByPaymentId(payment.getId()))
                    .extracting(Refund::getGatewayRefundId)
                    .containsExactly("rfnd_A");
        }

        @Test
        @DisplayName("answers with an empty list for a payment that was never refunded")
        void answersEmptyForANeverRefundedPayment() {
            assertThat(refundRepository.findAllByPaymentId(payment.getId())).isEmpty();
        }

        @Test
        @DisplayName("answers with an empty list for a payment that does not exist")
        void answersEmptyForAnUnknownPayment() {
            assertThat(refundRepository.findAllByPaymentId(999_999L)).isEmpty();
        }

        @Test
        @DisplayName("answers with an empty list for a null payment id")
        void answersEmptyForANullPaymentId() {
            assertThat(refundRepository.findAllByPaymentId(null)).isEmpty();
        }

        @Test
        @DisplayName("returns the refunds whatever their status")
        void returnsEveryStatus() {
            entityManager.persistAndFlush(refund(payment, "rfnd_A", "10.00", RefundStatus.PENDING));
            entityManager.persistAndFlush(refund(payment, "rfnd_B", "10.00", RefundStatus.SUCCESS));
            entityManager.persistAndFlush(refund(payment, "rfnd_C", "10.00", RefundStatus.FAILED));

            assertThat(refundRepository.findAllByPaymentId(payment.getId()))
                    .extracting(Refund::getStatus)
                    .containsExactlyInAnyOrder(RefundStatus.PENDING, RefundStatus.SUCCESS, RefundStatus.FAILED);
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
            Refund stored = refundRepository.save(refund(payment, "rfnd_A", "1000.00", RefundStatus.SUCCESS));
            entityManager.flush();

            assertThat(stored.getId()).isNotNull();
            assertThat(stored.getCreatedAt()).isNotNull();
            assertThat(stored.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("keeps the refunded amount with two decimals")
        void keepsTwoDecimals() {
            Refund stored = refundRepository.save(refund(payment, "rfnd_A", "1234.56", RefundStatus.SUCCESS));
            entityManager.flush();
            entityManager.clear();

            assertThat(refundRepository.findById(stored.getId()).orElseThrow().getRefundAmount())
                    .isEqualByComparingTo(new BigDecimal("1234.56"));
        }

        @Test
        @DisplayName("stores the status as readable text, not as an ordinal")
        void storesTheStatusAsText() {
            Refund stored = refundRepository.save(refund(payment, "rfnd_A", "10.00", RefundStatus.FAILED));
            entityManager.flush();
            entityManager.clear();

            Object status = entityManager.getEntityManager()
                    .createNativeQuery("select status from refunds where id = " + stored.getId())
                    .getSingleResult();

            assertThat(status).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("points back at its payment")
        void pointsBackAtItsPayment() {
            Refund stored = refundRepository.save(refund(payment, "rfnd_A", "10.00", RefundStatus.SUCCESS));
            entityManager.flush();
            entityManager.clear();

            assertThat(refundRepository.findById(stored.getId()).orElseThrow().getPayment().getId())
                    .isEqualTo(payment.getId());
        }

        @Test
        @DisplayName("refuses a refund that belongs to no payment")
        void refusesAnOrphanRefund() {
            assertThatThrownBy(() -> {
                entityManager.persist(refund(null, "rfnd_A", "10.00", RefundStatus.SUCCESS));
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("refuses a refund without an amount")
        void refusesARefundWithoutAnAmount() {
            Refund refund = refund(payment, "rfnd_A", "10.00", RefundStatus.SUCCESS);
            refund.setRefundAmount(null);

            assertThatThrownBy(() -> {
                entityManager.persist(refund);
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("refuses a refund without a status")
        void refusesARefundWithoutAStatus() {
            Refund refund = refund(payment, "rfnd_A", "10.00", null);

            assertThatThrownBy(() -> {
                entityManager.persist(refund);
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("refuses to store the same gateway refund id twice, a webhook may be redelivered")
        void refusesADuplicateGatewayRefundId() {
            entityManager.persistAndFlush(refund(payment, "rfnd_DUPLICATE", "10.00", RefundStatus.SUCCESS));

            assertThatThrownBy(() -> {
                entityManager.persist(refund(otherPayment, "rfnd_DUPLICATE", "10.00", RefundStatus.SUCCESS));
                entityManager.flush();
            })
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("GATEWAY_REFUND_ID");
        }

        @Test
        @DisplayName("tolerates a refund that never reached the gateway")
        void toleratesANullGatewayRefundId() {
            entityManager.persistAndFlush(refund(payment, null, "10.00", RefundStatus.PENDING));
            entityManager.persistAndFlush(refund(payment, null, "20.00", RefundStatus.PENDING));

            assertThat(refundRepository.findAllByPaymentId(payment.getId())).hasSize(2);
        }

        @Test
        @DisplayName("keeps a pending refund without a refunded timestamp")
        void keepsAPendingRefundUnstamped() {
            Refund refund = refund(payment, "rfnd_A", "10.00", RefundStatus.PENDING);
            refund.setRefundedAt(null);

            Refund stored = refundRepository.save(refund);
            entityManager.flush();
            entityManager.clear();

            assertThat(refundRepository.findById(stored.getId()).orElseThrow().getRefundedAt()).isNull();
        }

        @Test
        @DisplayName("keeps the gateway diagnosis of a failed refund")
        void keepsTheGatewayDiagnosis() {
            Refund refund = refund(payment, "rfnd_A", "10.00", RefundStatus.FAILED);
            refund.setGatewayErrorCode("REFUND_FAILED");
            refund.setGatewayErrorMessage("The bank refused the reversal");

            Refund stored = refundRepository.save(refund);
            entityManager.flush();
            entityManager.clear();

            Refund reloaded = refundRepository.findById(stored.getId()).orElseThrow();
            assertThat(reloaded.getGatewayErrorCode()).isEqualTo("REFUND_FAILED");
            assertThat(reloaded.getGatewayErrorMessage()).isEqualTo("The bank refused the reversal");
        }
    }

    // ==================================================================
    // partial refunds
    // ==================================================================

    @Test
    @DisplayName("a payment can be refunded in several instalments")
    void aPaymentCanBeRefundedInInstalments() {
        refundRepository.save(refund(payment, "rfnd_A", "1000.00", RefundStatus.SUCCESS));
        refundRepository.save(refund(payment, "rfnd_B", "2000.00", RefundStatus.SUCCESS));
        refundRepository.save(refund(payment, "rfnd_C", "5999.00", RefundStatus.SUCCESS));
        entityManager.flush();

        BigDecimal total = refundRepository.findAllByPaymentId(payment.getId()).stream()
                .map(Refund::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total).isEqualByComparingTo(new BigDecimal("8999.00"));
    }
}




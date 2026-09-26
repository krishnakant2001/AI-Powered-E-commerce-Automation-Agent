package com.strikerkk.aicommerce.payment_service.entity;

import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The payment entities")
class PaymentEntitiesTest {

    // ==================================================================
    // Payment
    // ==================================================================

    @Nested
    @DisplayName("Payment")
    class PaymentEntity {

        @Test
        @DisplayName("is built with everything the initiate flow knows")
        void isBuiltWithTheInitiateData() {
            Payment payment = Payment.builder()
                    .orderId(100L)
                    .userId(42L)
                    .amount(new BigDecimal("8999.00"))
                    .status(PaymentStatus.INITIATED)
                    .gateway(PaymentGateway.RAZORPAY)
                    .gatewayOrderId("order_A")
                    .build();

            assertThat(payment.getOrderId()).isEqualTo(100L);
            assertThat(payment.getUserId()).isEqualTo(42L);
            assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("8999.00"));
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
            assertThat(payment.getGateway()).isEqualTo(PaymentGateway.RAZORPAY);
        }

        @Test
        @DisplayName("never leaves the refund list null, the webhook walks it straight away")
        void neverLeavesTheRefundListNull() {
            // Both construction paths have to be safe: the builder is used by the service and the
            // no argument constructor is the one Hibernate calls when it loads a row.
            assertThat(Payment.builder().build().getRefunds()).isNotNull().isEmpty();
            assertThat(new Payment().getRefunds()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("can also be built by the no argument constructor JPA needs")
        void hasANoArgumentConstructor() {
            Payment payment = new Payment();
            payment.setOrderId(100L);

            assertThat(payment.getOrderId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("records the three moments of a payment life cycle")
        void recordsTheThreeMoments() {
            LocalDateTime now = LocalDateTime.now();
            Payment payment = Payment.builder().build();

            payment.setPaidAt(now);
            payment.setFailedAt(now);
            payment.setRefundedAt(now);

            assertThat(payment.getPaidAt()).isEqualTo(now);
            assertThat(payment.getFailedAt()).isEqualTo(now);
            assertThat(payment.getRefundedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("maps to the 'payment' table")
        void mapsToThePaymentTable() {
            assertThat(Payment.class.getAnnotation(Entity.class)).isNotNull();
            assertThat(Payment.class.getAnnotation(Table.class).name()).isEqualTo("payment");
        }

        @Test
        @DisplayName("stores the status and the gateway as text, a renumbered enum must not corrupt the ledger")
        void storesEnumsAsText() throws Exception {
            assertThat(Payment.class.getDeclaredField("status").getAnnotation(Enumerated.class).value())
                    .isEqualTo(EnumType.STRING);
            assertThat(Payment.class.getDeclaredField("gateway").getAnnotation(Enumerated.class).value())
                    .isEqualTo(EnumType.STRING);
        }

        @Test
        @DisplayName("requires an order, a user, an amount, a status and a gateway")
        void requiresTheCoreColumns() throws Exception {
            for (String field : new String[]{"orderId", "userId", "amount", "status", "gateway"}) {
                assertThat(Payment.class.getDeclaredField(field).getAnnotation(Column.class).nullable())
                        .as(field + " must be NOT NULL")
                        .isFalse();
            }
        }

        @Test
        @DisplayName("keeps the money at 10 digits with 2 decimals")
        void keepsTheMoneyPrecision() throws Exception {
            Column amount = Payment.class.getDeclaredField("amount").getAnnotation(Column.class);

            assertThat(amount.precision()).isEqualTo(10);
            assertThat(amount.scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("keeps both gateway ids unique, a webhook can be redelivered safely")
        void keepsBothGatewayIdsUnique() throws Exception {
            assertThat(Payment.class.getDeclaredField("gatewayOrderId").getAnnotation(Column.class).unique())
                    .isTrue();
            assertThat(Payment.class.getDeclaredField("gatewayPaymentId").getAnnotation(Column.class).unique())
                    .isTrue();
        }

        @Test
        @DisplayName("owns its refunds: cascaded, orphan removed and loaded lazily")
        void ownsItsRefunds() throws Exception {
            OneToMany association = Payment.class.getDeclaredField("refunds").getAnnotation(OneToMany.class);

            assertThat(association.mappedBy()).isEqualTo("payment");
            assertThat(association.cascade()).contains(jakarta.persistence.CascadeType.ALL);
            assertThat(association.orphanRemoval()).isTrue();
            assertThat(association.fetch()).isEqualTo(FetchType.LAZY);
        }

        @Test
        @DisplayName("never lets the creation stamp be updated")
        void neverUpdatesTheCreationStamp() throws Exception {
            Column createdAt = Payment.class.getDeclaredField("createdAt").getAnnotation(Column.class);

            assertThat(createdAt.updatable()).isFalse();
            assertThat(createdAt.nullable()).isFalse();
        }
    }

    // ==================================================================
    // Refund
    // ==================================================================

    @Nested
    @DisplayName("Refund")
    class RefundEntity {

        @Test
        @DisplayName("is built with everything the refund webhook knows")
        void isBuiltWithTheWebhookData() {
            Payment payment = Payment.builder().id(500L).build();
            LocalDateTime now = LocalDateTime.now();

            Refund refund = Refund.builder()
                    .payment(payment)
                    .refundAmount(new BigDecimal("1000.00"))
                    .status(RefundStatus.SUCCESS)
                    .gatewayRefundId("rfnd_A")
                    .reason("Refund processed by razorpay")
                    .refundedAt(now)
                    .build();

            assertThat(refund.getPayment()).isSameAs(payment);
            assertThat(refund.getRefundAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCESS);
            assertThat(refund.getGatewayRefundId()).isEqualTo("rfnd_A");
            assertThat(refund.getRefundedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("maps to the 'refunds' table")
        void mapsToTheRefundsTable() {
            assertThat(Refund.class.getAnnotation(Entity.class)).isNotNull();
            assertThat(Refund.class.getAnnotation(Table.class).name()).isEqualTo("refunds");
        }

        @Test
        @DisplayName("belongs to exactly one payment, loaded lazily")
        void belongsToOnePayment() throws Exception {
            ManyToOne association = Refund.class.getDeclaredField("payment").getAnnotation(ManyToOne.class);
            JoinColumn join = Refund.class.getDeclaredField("payment").getAnnotation(JoinColumn.class);

            assertThat(association.fetch()).isEqualTo(FetchType.LAZY);
            assertThat(join.name()).isEqualTo("payment_id");
            assertThat(join.nullable()).isFalse();
        }

        @Test
        @DisplayName("stores its status as text")
        void storesTheStatusAsText() throws Exception {
            assertThat(Refund.class.getDeclaredField("status").getAnnotation(Enumerated.class).value())
                    .isEqualTo(EnumType.STRING);
        }

        @Test
        @DisplayName("requires an amount and a status")
        void requiresTheCoreColumns() throws Exception {
            assertThat(Refund.class.getDeclaredField("refundAmount").getAnnotation(Column.class).nullable())
                    .isFalse();
            assertThat(Refund.class.getDeclaredField("status").getAnnotation(Column.class).nullable())
                    .isFalse();
        }

        @Test
        @DisplayName("keeps the refunded amount at 10 digits with 2 decimals")
        void keepsTheMoneyPrecision() throws Exception {
            Column amount = Refund.class.getDeclaredField("refundAmount").getAnnotation(Column.class);

            assertThat(amount.precision()).isEqualTo(10);
            assertThat(amount.scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("keeps the gateway refund id unique, a redelivered webhook cannot refund twice")
        void keepsTheGatewayRefundIdUnique() throws Exception {
            assertThat(Refund.class.getDeclaredField("gatewayRefundId").getAnnotation(Column.class).unique())
                    .isTrue();
        }

        @Test
        @DisplayName("can also be built by the no argument constructor JPA needs")
        void hasANoArgumentConstructor() {
            Refund refund = new Refund();
            refund.setGatewayRefundId("rfnd_A");

            assertThat(refund.getGatewayRefundId()).isEqualTo("rfnd_A");
        }
    }

    // ==================================================================
    // the enums
    // ==================================================================

    @Nested
    @DisplayName("the enums")
    class Enums {

        @Test
        @DisplayName("a payment walks through INITIATED, SUCCESS, FAILED and REFUNDED")
        void thePaymentStates() {
            assertThat(PaymentStatus.values()).containsExactly(
                    PaymentStatus.INITIATED, PaymentStatus.SUCCESS,
                    PaymentStatus.FAILED, PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("a refund walks through PENDING, SUCCESS and FAILED")
        void theRefundStates() {
            assertThat(RefundStatus.values()).containsExactly(
                    RefundStatus.PENDING, RefundStatus.SUCCESS, RefundStatus.FAILED);
        }

        @Test
        @DisplayName("Razorpay is the only gateway wired in")
        void razorpayIsTheOnlyGateway() {
            assertThat(PaymentGateway.values()).containsExactly(PaymentGateway.RAZORPAY);
        }

        @Test
        @DisplayName("the names are the values stored in the database, they must not drift")
        void theNamesAreTheStoredValues() {
            assertThat(PaymentStatus.INITIATED.name()).isEqualTo("INITIATED");
            assertThat(PaymentStatus.SUCCESS.name()).isEqualTo("SUCCESS");
            assertThat(PaymentStatus.FAILED.name()).isEqualTo("FAILED");
            assertThat(PaymentStatus.REFUNDED.name()).isEqualTo("REFUNDED");
            assertThat(RefundStatus.PENDING.name()).isEqualTo("PENDING");
            assertThat(PaymentGateway.RAZORPAY.name()).isEqualTo("RAZORPAY");
        }

        @Test
        @DisplayName("every name round trips through valueOf")
        void everyNameRoundTrips() {
            for (PaymentStatus status : PaymentStatus.values()) {
                assertThat(PaymentStatus.valueOf(status.name())).isEqualTo(status);
            }
            for (RefundStatus status : RefundStatus.values()) {
                assertThat(RefundStatus.valueOf(status.name())).isEqualTo(status);
            }
        }

        @Test
        @DisplayName("the status column is wide enough for the longest name")
        void theColumnIsWideEnough() throws Exception {
            int longest = java.util.Arrays.stream(PaymentStatus.values())
                    .mapToInt(s -> s.name().length())
                    .max()
                    .orElseThrow();

            assertThat(Payment.class.getDeclaredField("status").getAnnotation(Column.class).length())
                    .isGreaterThanOrEqualTo(longest);
        }
    }
}



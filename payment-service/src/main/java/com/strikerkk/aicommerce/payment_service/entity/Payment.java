package com.strikerkk.aicommerce.payment_service.entity;

import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "payment")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 20)
    private PaymentGateway gateway;

    // Razorpay specific fields
    @Column(name = "gateway_order_id", unique = true)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", unique = true)
    private String gatewayPaymentId;

    @Column(name = "gateway_signature")
    private String gatewaySignature;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "gateway_error_code")
    private String gatewayErrorCode;

    @Column(name = "gateway_error_message")
    private String gatewayErrorMessage;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    @OneToMany(
            mappedBy = "payment",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Refund> refunds = new ArrayList<>();
}

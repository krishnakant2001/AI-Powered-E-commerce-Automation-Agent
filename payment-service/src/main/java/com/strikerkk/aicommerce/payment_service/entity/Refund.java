package com.strikerkk.aicommerce.payment_service.entity;

import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "refunds")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "refund_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "gateway_refund_id", unique = true)
    private String gatewayRefundId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "gateway_error_code")
    private String gatewayErrorCode;

    @Column(name = "gateway_error_message")
    private String gatewayErrorMessage;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;       // set when refund status → SUCCESS

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}

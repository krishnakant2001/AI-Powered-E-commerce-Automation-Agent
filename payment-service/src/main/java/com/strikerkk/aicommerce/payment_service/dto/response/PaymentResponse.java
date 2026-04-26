package com.strikerkk.aicommerce.payment_service.dto.response;

import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentGateway gateway;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String failureReason;
    private LocalDateTime paidAt;
    private LocalDateTime failedAt;
    private LocalDateTime refundedAt;
    private List<RefundStatus> refunds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

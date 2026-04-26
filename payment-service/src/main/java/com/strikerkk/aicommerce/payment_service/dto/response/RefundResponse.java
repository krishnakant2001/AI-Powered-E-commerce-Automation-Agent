package com.strikerkk.aicommerce.payment_service.dto.response;

import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundResponse {
    private Long id;
    private Long paymentId;
    private BigDecimal refundAmount;
    private RefundStatus refundStatus;
    private String gatewayRefundId;
    private String reason;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
}

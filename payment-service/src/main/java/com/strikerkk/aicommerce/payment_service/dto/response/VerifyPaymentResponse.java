package com.strikerkk.aicommerce.payment_service.dto.response;

import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VerifyPaymentResponse {
    private Long paymentId;
    private Long orderId;
    private PaymentStatus status;
    private String message;
    private LocalDateTime paidAt;
}

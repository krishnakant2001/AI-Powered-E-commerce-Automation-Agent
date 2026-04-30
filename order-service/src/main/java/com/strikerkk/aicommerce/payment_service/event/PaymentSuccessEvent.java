package com.strikerkk.aicommerce.payment_service.event;

import lombok.Data;

@Data
public class PaymentSuccessEvent {
    private Long id;
    private Long userId;
    private Long orderId;
    private String paymentStatus;
}

package com.strikerkk.aicommerce.payment_service.dto.request;

import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import lombok.Data;

@Data
public class InitiatePaymentRequest {
    private Long orderId;
    private PaymentGateway gateway;
}

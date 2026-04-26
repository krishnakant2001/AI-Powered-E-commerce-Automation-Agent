package com.strikerkk.aicommerce.payment_service.dto.response;

import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InitiatePaymentResponse {
    private Long paymentId;
    private String gatewayOrderId;
    private BigDecimal amount;
    private String currency;
    private PaymentGateway gateway;
    private String keyId;
    private String paymentToken;
}

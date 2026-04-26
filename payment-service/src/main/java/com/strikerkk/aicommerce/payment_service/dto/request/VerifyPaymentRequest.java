package com.strikerkk.aicommerce.payment_service.dto.request;

import lombok.Data;

@Data
public class VerifyPaymentRequest {
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
}

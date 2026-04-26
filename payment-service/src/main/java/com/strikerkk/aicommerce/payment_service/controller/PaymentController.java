package com.strikerkk.aicommerce.payment_service.controller;

import com.strikerkk.aicommerce.payment_service.common.ApiResponse;
import com.strikerkk.aicommerce.payment_service.dto.request.InitiatePaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.request.VerifyPaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.response.InitiatePaymentResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.RefundResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.VerifyPaymentResponse;
import com.strikerkk.aicommerce.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<InitiatePaymentResponse>> initiatePayment(@RequestBody InitiatePaymentRequest request) {

        InitiatePaymentResponse response = paymentService.initiatePayment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment created successfully", response));
    }


    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<VerifyPaymentResponse>> verifyPayment(@RequestBody VerifyPaymentRequest request) {

        VerifyPaymentResponse response = paymentService.verifyPayment(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Payment verified", response));
    }

    @GetMapping("/{paymentId}/refunds")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getRefunds(@PathVariable Long paymentId) {
        List<RefundResponse> refundResponseList = paymentService.getRefundsByPaymentId(paymentId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Getting refund list", refundResponseList));
    }
}

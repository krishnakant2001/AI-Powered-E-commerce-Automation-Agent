package com.strikerkk.aicommerce.payment_service.service;

import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.Refund;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import com.strikerkk.aicommerce.payment_service.payment.VerifySignature;
import com.strikerkk.aicommerce.payment_service.repository.PaymentRepository;
import com.strikerkk.aicommerce.payment_service.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookService {

    private final VerifySignature verifySignature;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public void handleRazorpayWebhook(String payload, String razorpaySignature) {

        // Step 1: Verify webhook signature, Protect against fake payment
        if(!verifySignature.verifyWebhookSignature(payload, razorpaySignature)) {
            throw new RuntimeException("Invalid webhook signature");
        }

        // Step 2: Parse payload
        JSONObject webhookPayload = new JSONObject(payload);
        String event = webhookPayload.getString("event");

        log.info("Razorpay webhook received for event: {}", event);

        // Step 3: Route to correct handler based on event
        switch (event) {
            case "payment.captured" -> handlePaymentCaptured(webhookPayload);
            case "payment.failed"   -> handlePaymentFailed(webhookPayload);
            case "refund.processed" -> handleRefundProcessed(webhookPayload);
            default -> log.info("Unhandled webhook event: {}", event);
        }

    }

    private void handlePaymentCaptured(JSONObject webhookPayload) {
        JSONObject paymentEntity = webhookPayload
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String gatewayPaymentId = paymentEntity.getString("id");
        String gatewayOrderId = paymentEntity.getString("order_id");

        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // Update only if status still INITIATED - /verify may have already set it to success
        if(payment.getStatus() == PaymentStatus.INITIATED || payment.getStatus() == PaymentStatus.FAILED) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setGatewayPaymentId(gatewayPaymentId);
            payment.setPaidAt(LocalDateTime.now());

            paymentRepository.save(payment);
            log.info("Webhook: Payment SUCCESS for orderId: {}", payment.getOrderId());

            // TODO: Call Order Service to update status to CONFIRMED

        } else {
            log.info("Webhook: payment.captured received but payment already in {} status, skipping",
                    payment.getStatus());
        }
    }

    private void handlePaymentFailed(JSONObject webhookPayload) {
        JSONObject paymentEntity = webhookPayload
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String gatewayPaymentId = paymentEntity.getString("id");
        String gatewayOrderId = paymentEntity.getString("order_id");
        String errorCode = paymentEntity.optString("error_code", null);
        String errorDescription = paymentEntity.optString("error_description", null);
        String errorReason = paymentEntity.optString("error_reason", null);

        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // Only Update if status still Initiated
        if(payment.getStatus() == PaymentStatus.INITIATED) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayPaymentId(gatewayPaymentId);
            payment.setGatewayErrorMessage(errorDescription);
            payment.setGatewayErrorCode(errorCode);
            payment.setFailureReason(errorReason);
            payment.setFailedAt(LocalDateTime.now());

            paymentRepository.save(payment);
            log.info("Webhook: Payment FAILED for orderId: {}, reason: {}", payment.getOrderId(), errorReason);

        } else {
            log.info("Webhook: payment.failed received but payment already in {} status, skipping",
                    payment.getStatus());
        }
    }

    @Transactional
    private void handleRefundProcessed(JSONObject webhookPayload) {
        JSONObject refundEntity = webhookPayload
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String gatewayRefundId = refundEntity.getString("id");
        String gatewayPaymentId = refundEntity.getString("payment_id");

        int amountInPaise = refundEntity.getInt("amount");

        // Convert paise back to rupees
        BigDecimal refundAmount = BigDecimal.valueOf(amountInPaise)
                .divide(BigDecimal.valueOf(100));

        // Find the payment by gatewayPaymentId
        Payment payment = paymentRepository.findByGatewayPaymentId(gatewayPaymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));


        // Check if this refund already exists - prevent duplicate processing
        boolean alreadyProcessed = payment.getRefunds()
                .stream()
                .anyMatch(refund -> gatewayRefundId.equals(refund.getGatewayRefundId()));

        if (alreadyProcessed) {
            log.info("Webhook: Refund {} already processed, skipping", gatewayRefundId);
            return;
        }

        // Create Refund Record
        Refund refund = Refund.builder()
                .payment(payment)
                .refundAmount(refundAmount)
                .status(RefundStatus.SUCCESS)
                .gatewayRefundId(gatewayRefundId)
                .reason("Refund processed by razorpay")
                .refundedAt(LocalDateTime.now())
                .build();

        refundRepository.save(refund);

        // Update payment status to refunded
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(LocalDateTime.now());

        paymentRepository.save(payment);

        log.info("Webhook: Refund SUCCESS for paymentId: {} amount: {}",
                payment.getId(), refundAmount);

        // TODO: Call Order Service to update order status to REFUNDED
    }
}
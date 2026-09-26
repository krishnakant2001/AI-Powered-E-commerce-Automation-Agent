package com.strikerkk.aicommerce.payment_service.support;

import com.razorpay.Order;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderResponse;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderStatus;
import com.strikerkk.aicommerce.payment_service.dto.request.InitiatePaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.request.VerifyPaymentRequest;
import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.Refund;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.entity.enums.RefundStatus;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * One place for every piece of test data, so the expectations of a test read as a sentence
 * instead of a wall of setters.
 */
public final class TestDataFactory {

    public static final Long USER_ID = 42L;
    public static final Long OTHER_USER_ID = 99L;
    public static final Long ORDER_ID = 100L;
    public static final Long PAYMENT_ID = 500L;
    public static final Long REFUND_ID = 900L;

    /** The amount the order asks for, in rupees. 8999.00 rupees == 899900 paise. */
    public static final BigDecimal AMOUNT = new BigDecimal("8999.00");
    public static final int AMOUNT_IN_PAISE = 899900;

    public static final String GATEWAY_ORDER_ID = "order_TESTorder123";
    public static final String GATEWAY_PAYMENT_ID = "pay_TESTpayment123";
    public static final String GATEWAY_REFUND_ID = "rfnd_TESTrefund123";

    /**
     * The credentials of src/test/resources/application.properties. Every signature used by the
     * tests is computed with exactly these, so the real VerifySignature accepts them.
     */
    public static final String KEY_ID = "rzp_test_dummykey";
    public static final String KEY_SECRET = "dummysecret";
    public static final String WEBHOOK_SECRET = "dummywebhooksecret";

    public static final String RAZORPAY_SIGNATURE_HEADER = "X-Razorpay-Signature";
    public static final String USER_ID_HEADER = "X-user-id";

    private TestDataFactory() {
    }

    // ------------------------------------------------------------------
    // entities
    // ------------------------------------------------------------------

    /** A payment of {@link #USER_ID} that has been handed to Razorpay but is not paid yet. */
    public static Payment payment() {
        return payment(PAYMENT_ID, USER_ID, PaymentStatus.INITIATED);
    }

    public static Payment payment(Long id, Long userId, PaymentStatus status) {
        return Payment.builder()
                .id(id)
                .orderId(ORDER_ID)
                .userId(userId)
                .amount(AMOUNT)
                .status(status)
                .gateway(PaymentGateway.RAZORPAY)
                .gatewayOrderId(GATEWAY_ORDER_ID)
                .refunds(new ArrayList<>())
                .build();
    }

    /** A payment that Razorpay has already captured - it carries a gateway payment id. */
    public static Payment capturedPayment() {
        Payment payment = payment(PAYMENT_ID, USER_ID, PaymentStatus.SUCCESS);
        payment.setGatewayPaymentId(GATEWAY_PAYMENT_ID);
        payment.setPaidAt(LocalDateTime.now());
        return payment;
    }

    /** A refund that is not attached to any payment - handy for the pure mapping tests. */
    public static Refund refund() {
        return refund(REFUND_ID, null, AMOUNT, RefundStatus.SUCCESS);
    }

    public static Refund refund(Long id, Payment payment, BigDecimal amount, RefundStatus status) {
        return Refund.builder()
                .id(id)
                .payment(payment)
                .refundAmount(amount)
                .status(status)
                .gatewayRefundId(GATEWAY_REFUND_ID)
                .reason("Refund processed by razorpay")
                .refundedAt(LocalDateTime.now())
                .build();
    }

    // ------------------------------------------------------------------
    // client payloads
    // ------------------------------------------------------------------

    /** What order-service answers for {@code GET /orders/{orderId}} - payable, owned by USER_ID. */
    public static OrderResponse orderResponse() {
        return orderResponse(USER_ID, OrderStatus.PENDING, AMOUNT);
    }

    public static OrderResponse orderResponse(Long userId, OrderStatus status, BigDecimal needToPay) {
        OrderResponse response = new OrderResponse();
        response.setId(ORDER_ID);
        response.setUserId(userId);
        response.setTotalAmount(needToPay);
        response.setDeliveryCharges(BigDecimal.ZERO);
        response.setNeedToPay(needToPay);
        response.setStatus(status);
        return response;
    }

    // ------------------------------------------------------------------
    // requests
    // ------------------------------------------------------------------

    public static InitiatePaymentRequest initiatePaymentRequest() {
        return initiatePaymentRequest(ORDER_ID);
    }

    public static InitiatePaymentRequest initiatePaymentRequest(Long orderId) {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setOrderId(orderId);
        request.setGateway(PaymentGateway.RAZORPAY);
        return request;
    }

    /** A verify request whose signature really is the one Razorpay would have sent. */
    public static VerifyPaymentRequest signedVerifyRequest() {
        return verifyPaymentRequest(
                GATEWAY_ORDER_ID,
                GATEWAY_PAYMENT_ID,
                razorpaySignature(GATEWAY_ORDER_ID, GATEWAY_PAYMENT_ID));
    }

    public static VerifyPaymentRequest verifyPaymentRequest(String orderId, String paymentId, String signature) {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setRazorpayOrderId(orderId);
        request.setRazorpayPaymentId(paymentId);
        request.setRazorpaySignature(signature);
        return request;
    }

    // ------------------------------------------------------------------
    // Razorpay entities
    // ------------------------------------------------------------------

    /** The answer of {@code razorpayClient.orders.create(...)}. */
    public static Order razorpayOrder() {
        return razorpayOrder(GATEWAY_ORDER_ID);
    }

    public static Order razorpayOrder(String id) {
        return new Order(new JSONObject()
                .put("id", id)
                .put("entity", "order")
                .put("amount", AMOUNT_IN_PAISE)
                .put("currency", "INR")
                .put("status", "created"));
    }

    // ------------------------------------------------------------------
    // webhook payloads
    // ------------------------------------------------------------------

    public static String paymentCapturedPayload() {
        return paymentCapturedPayload(GATEWAY_PAYMENT_ID, GATEWAY_ORDER_ID);
    }

    public static String paymentCapturedPayload(String paymentId, String orderId) {
        return new JSONObject()
                .put("event", "payment.captured")
                .put("payload", new JSONObject()
                        .put("payment", new JSONObject()
                                .put("entity", new JSONObject()
                                        .put("id", paymentId)
                                        .put("order_id", orderId)
                                        .put("amount", AMOUNT_IN_PAISE)
                                        .put("status", "captured"))))
                .toString();
    }

    public static String paymentFailedPayload() {
        return paymentFailedPayload(GATEWAY_PAYMENT_ID, GATEWAY_ORDER_ID,
                "BAD_REQUEST_ERROR", "Payment was declined by the bank", "payment_failed");
    }

    public static String paymentFailedPayload(String paymentId,
                                              String orderId,
                                              String errorCode,
                                              String errorDescription,
                                              String errorReason) {
        JSONObject entity = new JSONObject()
                .put("id", paymentId)
                .put("order_id", orderId)
                .put("amount", AMOUNT_IN_PAISE)
                .put("status", "failed");

        if (errorCode != null) {
            entity.put("error_code", errorCode);
        }
        if (errorDescription != null) {
            entity.put("error_description", errorDescription);
        }
        if (errorReason != null) {
            entity.put("error_reason", errorReason);
        }

        return new JSONObject()
                .put("event", "payment.failed")
                .put("payload", new JSONObject().put("payment", new JSONObject().put("entity", entity)))
                .toString();
    }

    /**
     * A {@code refund.processed} payload shaped the way {@code WebhookService} currently reads it:
     * the refund fields are put under {@code payload.payment.entity}. Razorpay really sends them
     * under {@code payload.refund.entity} - see {@link #razorpayShapedRefundProcessedPayload()}.
     */
    public static String refundProcessedPayload() {
        return refundProcessedPayload(GATEWAY_REFUND_ID, GATEWAY_PAYMENT_ID, AMOUNT_IN_PAISE);
    }

    public static String refundProcessedPayload(String refundId, String paymentId, int amountInPaise) {
        return new JSONObject()
                .put("event", "refund.processed")
                .put("payload", new JSONObject()
                        .put("payment", new JSONObject()
                                .put("entity", new JSONObject()
                                        .put("id", refundId)
                                        .put("payment_id", paymentId)
                                        .put("amount", amountInPaise)
                                        .put("status", "processed"))))
                .toString();
    }

    /** The payload Razorpay actually posts for {@code refund.processed}. */
    public static String razorpayShapedRefundProcessedPayload() {
        return new JSONObject()
                .put("event", "refund.processed")
                .put("payload", new JSONObject()
                        .put("refund", new JSONObject()
                                .put("entity", new JSONObject()
                                        .put("id", GATEWAY_REFUND_ID)
                                        .put("payment_id", GATEWAY_PAYMENT_ID)
                                        .put("amount", AMOUNT_IN_PAISE)
                                        .put("status", "processed")))
                        .put("payment", new JSONObject()
                                .put("entity", new JSONObject()
                                        .put("id", GATEWAY_PAYMENT_ID)
                                        .put("order_id", GATEWAY_ORDER_ID)
                                        .put("amount", AMOUNT_IN_PAISE))))
                .toString();
    }

    public static String eventPayload(String event) {
        return new JSONObject().put("event", event).toString();
    }

    // ------------------------------------------------------------------
    // signatures
    // ------------------------------------------------------------------

    /** The checkout signature: HMAC_SHA256(orderId + "|" + paymentId, keySecret). */
    public static String razorpaySignature(String razorpayOrderId, String razorpayPaymentId) {
        return hmacSha256Hex(razorpayOrderId + "|" + razorpayPaymentId, KEY_SECRET);
    }

    /** The webhook signature: HMAC_SHA256(rawBody, webhookSecret). */
    public static String webhookSignature(String payload) {
        return hmacSha256Hex(payload, WEBHOOK_SECRET);
    }

    /** The reference implementation - deliberately written differently from the production one. */
    public static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign the test payload", e);
        }
    }
}


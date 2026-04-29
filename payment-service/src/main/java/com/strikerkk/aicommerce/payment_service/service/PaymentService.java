package com.strikerkk.aicommerce.payment_service.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.strikerkk.aicommerce.payment_service.dto.response.RefundResponse;
import com.strikerkk.aicommerce.payment_service.entity.Refund;
import com.strikerkk.aicommerce.payment_service.payment.VerifySignature;
import com.strikerkk.aicommerce.payment_service.auth.UserContext;
import com.strikerkk.aicommerce.payment_service.clients.OrderClient;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderResponse;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderStatus;
import com.strikerkk.aicommerce.payment_service.dto.request.InitiatePaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.request.VerifyPaymentRequest;
import com.strikerkk.aicommerce.payment_service.dto.response.InitiatePaymentResponse;
import com.strikerkk.aicommerce.payment_service.dto.response.VerifyPaymentResponse;
import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentGateway;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import com.strikerkk.aicommerce.payment_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.payment_service.repository.PaymentRepository;
import com.strikerkk.aicommerce.payment_service.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderClient orderClient;
    private final RazorpayClient razorpayClient;
    private final VerifySignature verifySignature;
    private final ModelMapper modelMapper;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Transactional
    public InitiatePaymentResponse initiatePayment(InitiatePaymentRequest request) {
        Long userId = Long.valueOf(UserContext.getUserId());

        OrderResponse orderResponse = orderClient.getOrderById(request.getOrderId()).getBody().getData();

        if(!orderResponse.getUserId().equals(userId)) {
            throw new UnauthorizedException("Order does not belong to this user");
        }

        if(orderResponse.getStatus() != OrderStatus.PENDING) {
            throw new RuntimeException("Order is not in PENDING state, Cannot initiate payment");
        }

        paymentRepository.findByOrderId(request.getOrderId())
                .ifPresent(existingPayment -> {
                    if(existingPayment.getStatus() == PaymentStatus.INITIATED ||
                            existingPayment.getStatus() == PaymentStatus.SUCCESS) {
                        throw new RuntimeException("Payment already exists for this order");
                    }
                });


        // Create Razorpay Order
        JSONObject razorpayOrderRequest = new JSONObject();
        razorpayOrderRequest.put(
                "amount", orderResponse.getNeedToPay().multiply(BigDecimal.valueOf(100)).intValue()
        );
        razorpayOrderRequest.put("currency", "INR");
        razorpayOrderRequest.put("receipt", "order_" + request.getOrderId());

        Order razorpayOrder;
        try {
            razorpayOrder = razorpayClient.orders.create(razorpayOrderRequest);
            log.info("Razorpay order created: {}" , (Object) razorpayOrder.get("id"));
        }
        catch (RazorpayException e) {
            log.error("Failed to create the Razorpay order for orderId: {}", request.getOrderId(), e);
            throw new RuntimeException("Failed to create Payment with Razorpay: " + e.getMessage());
        }

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(userId)
                .amount(orderResponse.getNeedToPay())
                .status(PaymentStatus.INITIATED)
                .gateway(PaymentGateway.RAZORPAY)
                .gatewayOrderId(razorpayOrder.get("id"))
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment saved with id: {} for orderId: {}", savedPayment.getId(), request.getOrderId());

        InitiatePaymentResponse response = modelMapper.map(savedPayment, InitiatePaymentResponse.class);
        response.setPaymentId(savedPayment.getId());
        response.setCurrency("INR");
        response.setKeyId(razorpayKeyId);

        return response;

    }

    @Transactional
    public VerifyPaymentResponse verifyPayment(VerifyPaymentRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());

        Payment payment = paymentRepository.findByGatewayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new RuntimeException("Payment not found for this Razorpay order"));

        if(!payment.getUserId().equals(userId)) {
            throw new RuntimeException("Payment does not belong to this user");
        }

        if(payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new RuntimeException("Payment already verified successfully");
        }

        boolean isValidSignature = verifySignature
                .verifyRazorpaySignature(request.getRazorpayOrderId(),
                        request.getRazorpayPaymentId(),
                        request.getRazorpaySignature()
                );

        if(isValidSignature) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setGatewayPaymentId(request.getRazorpayPaymentId());
            payment.setGatewaySignature(request.getRazorpaySignature());
            payment.setPaidAt(LocalDateTime.now());

            paymentRepository.save(payment);
            log.info("Payment verified successfully for orderId: {}", payment.getOrderId());

            // Update Order status to CONFIRMED via Feign or kafka
            VerifyPaymentResponse response = new VerifyPaymentResponse();
            response.setPaymentId(payment.getId());
            response.setOrderId(payment.getOrderId());
            response.setStatus(PaymentStatus.SUCCESS);
            response.setMessage("Payment verified successfully");
            response.setPaidAt(payment.getPaidAt());

            return response;
        }
        else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayPaymentId(request.getRazorpayPaymentId());
            payment.setFailureReason("Payment signature failure failed");
            payment.setGatewayErrorCode("SIGNATURE_VERIFICATION_FAILED");
            payment.setGatewayErrorMessage("razorpay_signature does not match expected signature");
            payment.setFailedAt(LocalDateTime.now());

            paymentRepository.save(payment);
            log.warn("Payment signature verification failed for orderId: {}", payment.getOrderId());

            VerifyPaymentResponse response = new VerifyPaymentResponse();
            response.setPaymentId(payment.getId());
            response.setOrderId(payment.getOrderId());
            response.setStatus(PaymentStatus.FAILED);
            response.setMessage("Payment failed due to some reason");
            response.setPaidAt(payment.getPaidAt());

            return response;
        }
    }

    public List<RefundResponse> getRefundsByPaymentId(Long paymentId) {
        Long userId = Long.valueOf(UserContext.getUserId());

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if(!payment.getUserId().equals(userId)) {
            throw new RuntimeException("Payment does not belong to this user");
        }

        List<Refund> refundList = refundRepository.findAllByPaymentId(paymentId);

        return refundList.stream()
                .map(refund -> modelMapper.map(refund, RefundResponse.class))
                .toList();
    }
}
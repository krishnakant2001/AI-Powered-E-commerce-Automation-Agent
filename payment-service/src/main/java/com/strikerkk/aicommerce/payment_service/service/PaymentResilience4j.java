package com.strikerkk.aicommerce.payment_service.service;

import com.strikerkk.aicommerce.payment_service.clients.OrderClient;
import com.strikerkk.aicommerce.payment_service.dto.clientResponse.OrderResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentResilience4j {

    private final OrderClient orderClient;

    @CircuitBreaker(name = "order-service-call", fallbackMethod = "getOrderByIdFallback")
    @Retry(name = "order-service-call")
    public OrderResponse getOrderById(Long orderId) {
        return orderClient.getOrderById(orderId).getBody().getData();
    }

    public OrderResponse getOrderFallback(Long orderId, Throwable ex) {
        throw new RuntimeException("Order service unavailable", ex);
    }
}

package com.strikerkk.aicommerce.cart_service.service;

import com.strikerkk.aicommerce.cart_service.clients.ProductClient;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CartResilience4j {

    private final ProductClient productClient;

    @Retry(name = "product-service-call")
    @CircuitBreaker(name = "product-service-call", fallbackMethod = "getItemDetailsFallback")
    public ProductCartResponse getItemDetails(Long productId, Long variantId) {
        return productClient.getProductItemDetails(productId, variantId);
    }

    public ProductCartResponse getItemDetailsFallback(Long productId, Long variantId, Throwable ex) {
        throw new RuntimeException("Product service unavailable", ex);
    }
}

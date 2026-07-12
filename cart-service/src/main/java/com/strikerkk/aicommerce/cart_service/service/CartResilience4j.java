package com.strikerkk.aicommerce.cart_service.service;

import com.strikerkk.aicommerce.cart_service.clients.ProductClient;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CartResilience4j {

    private final ProductClient productClient;

    @Retry(name = "product-service-call")
    @CircuitBreaker(name = "product-service-call", fallbackMethod = "getItemDetailsFallback")
    public ProductCartResponse getItemDetails(Long productId, Long variantId) {
        return productClient.getProductItemDetails(productId, variantId);
    }

    public ProductCartResponse getItemDetailsFallback(Long productId, Long variantId, Throwable ex) {
        log.error("Product Service unavailable for productId={}, variantId={} — circuit open or retries exhausted, Cause: {}",
                productId, variantId, ex.getMessage());
        throw new RuntimeException("Product Service unavailable", ex);
    }
}

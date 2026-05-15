package com.strikerkk.aicommerce.order_service.service;

import com.strikerkk.aicommerce.order_service.clients.ProductClient;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductItemResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderResilience4j {

    private final ProductClient productClient;

    @Retry(name = "product-service-call")
    @CircuitBreaker(name = "product-service-call", fallbackMethod = "getItemDetailsFallback")
    public ProductItemResponse getItemDetails(Long productId, Long variantId) {
        return productClient.getProductItemDetails(productId, variantId);
    }

    public ProductItemResponse getItemDetailsFallback(Long productId, Long variantId, Throwable ex) {
        throw new RuntimeException("Product service unavailable", ex);
    }
}

package com.strikerkk.aicommerce.order_service.clients;

import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductItemResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/products/{productId}/variants/{variantId}/item-info")
    ProductItemResponse getProductItemDetails(@PathVariable Long productId, @PathVariable Long variantId);
}

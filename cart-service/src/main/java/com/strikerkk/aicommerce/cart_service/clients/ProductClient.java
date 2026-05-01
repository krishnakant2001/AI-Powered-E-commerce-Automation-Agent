package com.strikerkk.aicommerce.cart_service.clients;

import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/products/{productId}/variants/{variantId}/item-info")
    ProductCartResponse getProductItemDetails(@PathVariable Long productId, @PathVariable Long variantId);

}

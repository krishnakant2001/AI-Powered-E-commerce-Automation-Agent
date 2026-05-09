package com.strikerkk.aicommerce.agent_service.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service", path = "/products")
public interface ProductServiceClient {

    @GetMapping("/all")
    String getAllProducts(@RequestParam(required = false) String search,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) Integer page,
                          @RequestParam(required = false) Integer size);


    @GetMapping("/details/{productId}")
    String getProductDetails(@PathVariable Long productId);


    @GetMapping("/{productId}/variants/{variantId}/item-info")
    String getProductItemDetails(@PathVariable Long productId, @PathVariable Long variantId);
}

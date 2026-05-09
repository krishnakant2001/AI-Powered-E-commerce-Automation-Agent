package com.strikerkk.aicommerce.agent_service.clients;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "cart-service", path = "/cart")
public interface CartServiceClient {

    @GetMapping("")
    String allCartItems();

    @PostMapping("/items")
    String addItemToCart(@RequestBody String requestBody);

    @PatchMapping("/items/{cartItemId}")
    String updateCartItem(@PathVariable Long cartItemId, @RequestBody String requestBody);

    @DeleteMapping("/clear")
    String clearCart();
}

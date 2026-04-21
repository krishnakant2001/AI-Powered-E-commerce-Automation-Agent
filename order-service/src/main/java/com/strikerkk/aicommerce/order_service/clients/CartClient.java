package com.strikerkk.aicommerce.order_service.clients;

import com.strikerkk.aicommerce.order_service.dto.ClientResponse.CartItemResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "cart-service")
public interface CartClient {

    @GetMapping("/cart/items")
    List<CartItemResponse> getCartItems();

    @DeleteMapping("/cart/clear")
    void clearCart();

}

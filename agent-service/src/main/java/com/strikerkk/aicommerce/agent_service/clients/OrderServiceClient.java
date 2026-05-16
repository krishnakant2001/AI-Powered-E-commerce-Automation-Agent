package com.strikerkk.aicommerce.agent_service.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service", path = "/orders")
public interface OrderServiceClient {

    @PostMapping(value = "", consumes = "application/json")
    String placeOrder(@RequestBody String requestBody);

    @PostMapping("/buy-now")
    String buyNow(@RequestBody String requestBody);

    @GetMapping("/{orderId}")
    String getOrderById(@PathVariable Long orderId);

    @GetMapping("/my-orders")
    String getMyOrders();

    @GetMapping("/{orderId}/items")
    String getOrderItems(@PathVariable Long orderId);

}

package com.strikerkk.aicommerce.payment_service.clients;

import com.strikerkk.aicommerce.payment_service.common.ApiResponse;
import com.strikerkk.aicommerce.payment_service.dto.ClientResponse.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service")
public interface OrderClient {

    @GetMapping("/orders/{orderId}")
    ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long orderId);
}

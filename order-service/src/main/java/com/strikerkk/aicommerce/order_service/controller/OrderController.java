package com.strikerkk.aicommerce.order_service.controller;

import com.strikerkk.aicommerce.order_service.common.ApiResponse;
import com.strikerkk.aicommerce.order_service.dto.request.PlaceOrderRequest;
import com.strikerkk.aicommerce.order_service.dto.response.OrderResponse;
import com.strikerkk.aicommerce.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(@RequestBody PlaceOrderRequest request) {
        OrderResponse orderResponse = orderService.placeOrder(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("You order has been successfully placed", orderResponse));
    }

    @GetMapping("/checking")
    public ResponseEntity<ApiResponse<Void>> check() {

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("Fetched"));
    }
}

package com.strikerkk.aicommerce.order_service.controller;

import com.strikerkk.aicommerce.order_service.common.ApiResponse;
import com.strikerkk.aicommerce.order_service.dto.request.PlaceOrderRequest;
import com.strikerkk.aicommerce.order_service.dto.response.OrderItemResponse;
import com.strikerkk.aicommerce.order_service.dto.response.OrderResponse;
import com.strikerkk.aicommerce.order_service.dto.response.OrderSummaryResponse;
import com.strikerkk.aicommerce.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
                .body(ApiResponse.success("You order has been placed, Payment is pending", orderResponse));
    }

    @PostMapping("/buy-now")
    public ResponseEntity<ApiResponse<OrderResponse>> buyNow(@RequestBody PlaceOrderRequest request) {
        OrderResponse orderResponse = orderService.buyNow(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Your order via buy now has been placed, Payment is pending", orderResponse));
    }


    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long orderId) {
        OrderResponse orderResponse = orderService.getOrderById(orderId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Fetched order details successfully", orderResponse));
    }


    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> getMyOrders() {
        List<OrderSummaryResponse> summaryResponses = orderService.getOrdersByUserId();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Order summary response", summaryResponses));
    }


    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long orderId) {
        OrderResponse orderResponse = orderService.cancelOrder(orderId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("You order has been cancelled", orderResponse));
    }


    @GetMapping("/{orderId}/items")
    public ResponseEntity<ApiResponse<List<OrderItemResponse>>> getOrderItems(@PathVariable Long orderId) {
        List<OrderItemResponse> orderItemResponseList = orderService.getOrderItems(orderId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Fetched order items successfully", orderItemResponseList));
    }
}

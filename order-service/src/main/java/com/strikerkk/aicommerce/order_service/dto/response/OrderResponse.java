package com.strikerkk.aicommerce.order_service.dto.response;

import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private Long id;
    private Long userId;
    private Long addressId;
    private String address;
    private BigDecimal totalAmount;
    private BigDecimal deliveryCharges;
    private BigDecimal needToPay;
    private OrderStatus status;
    private List<OrderItemResponse> orderItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

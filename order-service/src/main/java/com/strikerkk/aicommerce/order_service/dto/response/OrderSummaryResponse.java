package com.strikerkk.aicommerce.order_service.dto.response;

import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderSummaryResponse {
    private Long id;
    private String firstItemName;
    private String firstItemImageUrl;
    private Integer totalItems;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
}

package com.strikerkk.aicommerce.cart_service.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CartResponse {

    private Long cartId;
    private Long userId;

    private List<CartItemResponse> items;

    private BigDecimal totalAmount;

    private Integer totalItems;

    private Integer totalUniqueItems;

    // --- Enrichment status flag ---
    // true  → productDetails fields are populated (Product Service was reachable)
    // false → productDetails is null in each item (graceful degradation)
    private boolean enriched;


    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

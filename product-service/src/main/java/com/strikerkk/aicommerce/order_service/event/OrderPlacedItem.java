package com.strikerkk.aicommerce.order_service.event;

import lombok.Data;

@Data
public class OrderPlacedItem {
    private Long id;
    private Long productId;
    private Long variantId;
    private Integer quantity;
}

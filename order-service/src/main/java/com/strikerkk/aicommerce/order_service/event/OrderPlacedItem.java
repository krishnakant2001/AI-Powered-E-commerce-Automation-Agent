package com.strikerkk.aicommerce.order_service.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderPlacedItem {
    private Long id;
    private Long productId;
    private Long variantId;
    private Integer quantity;
}

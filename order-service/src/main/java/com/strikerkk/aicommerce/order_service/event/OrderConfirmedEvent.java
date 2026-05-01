package com.strikerkk.aicommerce.order_service.event;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OrderConfirmedEvent {
    private Long orderId;
    private List<OrderPlacedItem> orderPlacedItems;
}

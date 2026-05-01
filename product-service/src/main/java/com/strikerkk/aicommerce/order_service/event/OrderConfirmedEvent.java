package com.strikerkk.aicommerce.order_service.event;

import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmedEvent {
    private Long orderId;
    private List<OrderPlacedItem> orderPlacedItems;
}

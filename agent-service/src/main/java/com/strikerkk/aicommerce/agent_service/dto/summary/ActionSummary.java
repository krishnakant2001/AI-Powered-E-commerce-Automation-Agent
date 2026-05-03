package com.strikerkk.aicommerce.agent_service.dto.summary;

import lombok.Data;

@Data
public class ActionSummary {

    private String actionType;      // "ORDER_PLACED", "ADDED_TO_CART", "PAYMENT_INITIATED"
    private String resourceId;      // orderId, cartItemId, paymentId
    private String details;

}

package com.strikerkk.aicommerce.order_service.dto.request;

import lombok.Data;

@Data
public class PlaceOrderRequest {
    private Long userId;
    private Long addressId;
    private Long productId;
    private Long variantId;
}

package com.strikerkk.aicommerce.cart_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCartItemRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    // Only quantity is updatable.
    // productId and variantId are immutable after item is added —
    // changing product/variant = remove old + add new item.

}

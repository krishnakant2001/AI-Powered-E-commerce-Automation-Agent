package com.strikerkk.aicommerce.cart_service.dto.request;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddCartItemRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Variant ID is required")
    private Long variantId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    // NOTE: priceAtAdd is intentionally EXCLUDED here.
    // Price must always be fetched server-side from Product Service
    // to prevent client-side price tampering.
}

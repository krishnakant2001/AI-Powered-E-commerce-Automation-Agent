package com.strikerkk.aicommerce.product_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVariantRequest {

    private String size;

    private String color;

    @NotNull(message = "Stock count is required")
    @Min(value = 0, message = "Stock count cannot be negative")
    private Integer stockCount;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price override must be greater than 0")
    private BigDecimal priceOverride;

}

package com.strikerkk.aicommerce.product_service.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVariantResponse {

    private Long id;
    private String size;
    private String color;
    private Integer stockCount;
    private BigDecimal priceOverride;
}

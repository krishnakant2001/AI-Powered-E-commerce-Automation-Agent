package com.strikerkk.aicommerce.order_service.dto.ClientResponse;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductItemResponse {

    // Product
    private Long productId;
    private String productName;
    private String brandName;
    private BigDecimal price;
    private Boolean isAvailable;

    // Variant
    private Long variantId;
    private String size;
    private String color;
    private Boolean inStock;

    // Image
    private String imageUrl;
}

package com.strikerkk.aicommerce.order_service.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private Long variantId;
    private String productName;
    private String productBrand;
    private String productImageUrl;
    private String size;
    private String color;
    private Integer quantity;
    private BigDecimal priceAtOrder;
    private BigDecimal lineTotal;
}

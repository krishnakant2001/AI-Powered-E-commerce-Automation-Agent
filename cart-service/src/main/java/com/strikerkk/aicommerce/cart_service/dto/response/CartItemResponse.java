package com.strikerkk.aicommerce.cart_service.dto.response;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemResponse {

    private Long id;

    private Long productId;
    private Long variantId;

    private Integer quantity;

    private BigDecimal priceAtAdd;

    private BigDecimal itemTotal;

    private ProductDetails productDetails;
}

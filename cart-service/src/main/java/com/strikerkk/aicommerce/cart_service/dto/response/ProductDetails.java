package com.strikerkk.aicommerce.cart_service.dto.response;

import lombok.Data;

@Data
public class ProductDetails {

    private String productName;
    private String productBrand;
    private String productImageUrl;

    private String variantName;
    private String variantSku;
}

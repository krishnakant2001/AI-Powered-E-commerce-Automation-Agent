package com.strikerkk.aicommerce.cart_service.dto.response;

import lombok.Data;

@Data
public class ProductDetails {

    private String productName;
    private String productImageUrl;
    private String productBrand;

    private String variantName;
    private String variantSku;
}

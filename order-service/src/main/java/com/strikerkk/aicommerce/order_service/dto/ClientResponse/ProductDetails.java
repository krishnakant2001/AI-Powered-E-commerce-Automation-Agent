package com.strikerkk.aicommerce.order_service.dto.ClientResponse;

import lombok.Data;

@Data
public class ProductDetails {
    private String productName;
    private String productBrand;
    private String productImageUrl;
    private String variantName;
    private String size;
    private String color;
    private String variantSku;
}

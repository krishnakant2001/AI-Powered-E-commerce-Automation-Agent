package com.strikerkk.aicommerce.product_service.dto.response;

import lombok.Data;

@Data
public class ProductImageResponse {

    private Long id;
    private String imageUrl;
    private Boolean isPrimary;
}

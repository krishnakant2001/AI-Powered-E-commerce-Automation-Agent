package com.strikerkk.aicommerce.product_service.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResponse {

    private Long id;
    private String name;
    private String brand;
    private String description;
    private BigDecimal price;
    private String category;
    private Integer stockCount;
    private Boolean isAvailable;
    private LocalDateTime createdAt;
    private List<ProductVariantResponse> variants;
    private List<ProductImageResponse> images;
}

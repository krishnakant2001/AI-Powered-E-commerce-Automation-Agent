package com.strikerkk.aicommerce.product_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProductImageRequest {

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private Boolean isPrimary = false;
}

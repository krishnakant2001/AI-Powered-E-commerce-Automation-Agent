package com.strikerkk.aicommerce.product_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageRequest {

    @NotNull(message = "Image file is required")
    private MultipartFile image;

    private Boolean isPrimary = false;
}

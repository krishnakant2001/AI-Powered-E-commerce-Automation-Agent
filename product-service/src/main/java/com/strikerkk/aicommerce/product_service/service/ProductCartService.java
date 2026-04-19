package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.dto.response.ProductCartResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductCartService {

    private final ProductVariantRepository variantRepository;

    public ProductCartResponse getProductCartDetails(Long productId, Long variantId) {

        ProductVariant productVariant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow( () -> new ResourceNotFoundException("Product variant is not found"));

        Product product = productVariant.getProduct();

        ProductCartResponse productCartResponse = new ProductCartResponse();

        productCartResponse.setProductId(product.getId());
        productCartResponse.setProductName(product.getName());
        productCartResponse.setBrandName(product.getBrand());
        productCartResponse.setPrice(product.getPrice());
        productCartResponse.setIsAvailable(product.getIsAvailable());

        productCartResponse.setVariantId(productVariant.getId());
        productCartResponse.setSize(productVariant.getSize());
        productCartResponse.setColor(productVariant.getColor());
        productCartResponse.setInStock(productVariant.getStockCount() > 0);


        product.getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .findFirst()
                .ifPresent(img -> productCartResponse.setImageUrl(img.getUrl()));

        return productCartResponse;
    }
}

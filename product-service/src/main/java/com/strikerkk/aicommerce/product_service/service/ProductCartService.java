package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.dto.clientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCartService {

    private final ProductVariantRepository variantRepository;

    @Transactional(readOnly = true)
    public ProductItemResponse getProductItemDetails(Long productId, Long variantId) {

        log.info("Getting product details with productId={} and variantId={}", productId, variantId);

        ProductVariant productVariant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow( () -> new ResourceNotFoundException("Product variant is not found"));

        Product product = productVariant.getProduct();

        String primaryImageUrl = product.getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(null);

        return ProductItemResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .brandName(product.getBrand())
                .price(productVariant.getPriceOverride())
                .isAvailable(product.getIsAvailable())
                .variantId(productVariant.getId())
                .size(productVariant.getSize())
                .color(productVariant.getColor())
                .inStock(productVariant.getStockCount() > 0)
                .imageUrl(primaryImageUrl)
                .build();
    }
}

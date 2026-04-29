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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCartService {

    private final ProductVariantRepository variantRepository;

    public ProductItemResponse getProductItemDetails(Long productId, Long variantId) {

        log.info("Getting product details which has to be add in user cart or order via buy now");

        ProductVariant productVariant = variantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow( () -> new ResourceNotFoundException("Product variant is not found"));

        Product product = productVariant.getProduct();

        ProductItemResponse productItemResponse = new ProductItemResponse();

        productItemResponse.setProductId(product.getId());
        productItemResponse.setProductName(product.getName());
        productItemResponse.setBrandName(product.getBrand());
        productItemResponse.setPrice(product.getPrice());
        productItemResponse.setIsAvailable(product.getIsAvailable());

        productItemResponse.setVariantId(productVariant.getId());
        productItemResponse.setSize(productVariant.getSize());
        productItemResponse.setColor(productVariant.getColor());
        productItemResponse.setInStock(productVariant.getStockCount() > 0);


        product.getImages().stream()
                .filter(ProductImage::getIsPrimary)
                .findFirst()
                .ifPresent(img -> productItemResponse.setImageUrl(img.getUrl()));

        return productItemResponse;
    }
}

package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.dto.request.ProductVariantRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductVariantResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.helper.ProductOwnershipValidator;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ProductOwnershipValidator productOwnershipValidator;
    private final ModelMapper modelMapper;

    @Transactional
    public ProductVariantResponse createProductVariant(ProductVariantRequest request, Long productId) {

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Authorization check
        productOwnershipValidator.validate(product);

        // Create Product Variant
        ProductVariant productVariant = ProductVariant.builder()
                .product(product)
                .size(request.getSize())
                .color(request.getColor())
                .stockCount(request.getStockCount())
                .priceOverride(request.getPriceOverride())
                .build();

        ProductVariant savedProductVariant = productVariantRepository.save(productVariant);

        log.info("Create product variant for product_id={}", productId);

        return modelMapper.map(savedProductVariant, ProductVariantResponse.class);
    }

    @Transactional
    public ProductVariantResponse updateProductVariant(ProductVariantRequest request, Long productId, Long variantId) {

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Check if variant belongs to that product
        ProductVariant productVariant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found for this product"));

        // Authorization check
        productOwnershipValidator.validate(product);

        // Update fields
        productVariant.setSize(request.getSize());
        productVariant.setColor(request.getColor());
        productVariant.setStockCount(request.getStockCount());
        productVariant.setPriceOverride(request.getPriceOverride());

        ProductVariant updatedVariant = productVariantRepository.save(productVariant);

        log.info("Update product variant for product_id={} and variant_id={}", productId, variantId);

        return modelMapper.map(updatedVariant, ProductVariantResponse.class);
    }

    @Transactional
    public void deleteProductVariant(Long productId, Long variantId) {

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Authorization check
        productOwnershipValidator.validate(product);

        // Check if variant belongs to that product
        ProductVariant productVariant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found for this product"));

        log.info("Delete product variant of product_id={} and variant_id={}", productId, variantId);

        productVariantRepository.delete(productVariant);
    }
}

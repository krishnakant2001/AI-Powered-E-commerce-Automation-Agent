package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.dto.request.ProductImageRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductImageResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.product_service.repository.ProductImageRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;

    public ProductImageResponse addProductImage(ProductImageRequest request, Long productId) {

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Add Product Image
        ProductImage productImage = ProductImage.builder()
                .product(product)
                .url(request.getImageUrl())
                .isPrimary(request.getIsPrimary())
                .build();

        ProductImage savedProductImage = productImageRepository.save(productImage);

        log.info("Create product image of product_id={}}", productId);

        return modelMapper.map(savedProductImage, ProductImageResponse.class);
    }


    public ProductImageResponse updateProductImage(ProductImageRequest request, Long productId, Long imageId) {

        String userId = UserContext.getUserId();

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Authorization check
        if (!product.getCreatedBy().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to update this image");
        }

        // Check if image belongs to that product
        ProductImage productImage = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for this product"));

        if(Boolean.TRUE.equals(request.getIsPrimary())) {
            productImageRepository.restPrimaryImages(productId);
        }
        productImage.setIsPrimary(request.getIsPrimary());

        ProductImage updatedImage = productImageRepository.save(productImage);

        log.info("Update product image of product_id={} and image_id={}", productId, imageId);

        return modelMapper.map(updatedImage, ProductImageResponse.class);
    }


    public void deleteProductImage(Long productId, Long imageId) {

        String userId = UserContext.getUserId();

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Authorization check
        if (!product.getCreatedBy().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to delete this image");
        }

        // Check if image belongs to that product
        ProductImage productImage = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for this product"));

        log.info("Delete product image of product_id={} and image_id={}", productId, imageId);

        productImageRepository.delete(productImage);
    }

}

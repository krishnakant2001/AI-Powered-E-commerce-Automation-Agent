package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.dto.request.ProductImageRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductImageResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.helper.ProductOwnershipValidator;
import com.strikerkk.aicommerce.product_service.repository.ProductImageRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ProductOwnershipValidator productOwnershipValidator;
    private final S3ImageService s3ImageService;
    private final ModelMapper modelMapper;

    @Transactional
    public ProductImageResponse addProductImage(ProductImageRequest request, Long productId) {

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Authorization check
        productOwnershipValidator.validate(product);

        // Add Product Image in S3 bucket
        String imageKey = s3ImageService.uploadImage(request.getImage(), productId.toString());

        // Add Product Image
        ProductImage productImage = ProductImage.builder()
                .product(product)
                .url(imageKey)        // store the S3 key, not a raw URL
                .isPrimary(request.getIsPrimary())
                .build();

        try {
            ProductImage savedProductImage = productImageRepository.save(productImage);
            return modelMapper.map(savedProductImage, ProductImageResponse.class);
        } catch (Exception ex) {
            log.error("DB save failed, cleaning up S3 object. key={}", imageKey);
            s3ImageService.deleteImage(imageKey); // cleanup
            throw ex;
        }
    }

    @Transactional
    public ProductImageResponse updateProductImage(ProductImageRequest request, Long productId, Long imageId) {

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Authorization check
        productOwnershipValidator.validate(product);

        // Check if image belongs to that product
        ProductImage productImage = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for this product"));

        if (request.getImage() != null && !request.getImage().isEmpty()) {
            String newImageKey = s3ImageService.updateImage(request.getImage(), productImage.getUrl(), productId.toString());
            productImage.setUrl(newImageKey);
        }

        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            productImageRepository.restPrimaryImages(productId);
        }

        productImage.setIsPrimary(request.getIsPrimary());
        ProductImage updatedImage = productImageRepository.save(productImage);

        log.info("Update product image of product_id={} and image_id={}", productId, imageId);

        return modelMapper.map(updatedImage, ProductImageResponse.class);
    }


    @Transactional
    public void deleteProductImage(Long productId, Long imageId) {

        String userId = UserContext.getUserId();

        // Check if product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Authorization check
        productOwnershipValidator.validate(product);

        // Check if image belongs to that product
        ProductImage productImage = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found for this product"));

        try {
            s3ImageService.deleteImage(productImage.getUrl());
        } catch (Exception ex) {
            log.error("S3 delete failed for key={}", productImage.getUrl());
            throw ex;
        }

        productImageRepository.delete(productImage);

        log.info("Delete product image of product_id={} and image_id={}", productId, imageId);
    }
}

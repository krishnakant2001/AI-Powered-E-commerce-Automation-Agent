package com.strikerkk.aicommerce.product_service.controller;

import com.strikerkk.aicommerce.product_service.common.ApiResponse;
import com.strikerkk.aicommerce.product_service.dto.request.ProductImageRequest;
import com.strikerkk.aicommerce.product_service.dto.request.ProductRequest;
import com.strikerkk.aicommerce.product_service.dto.request.ProductVariantRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductImageResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductVariantResponse;
import com.strikerkk.aicommerce.product_service.service.ProductImageService;
import com.strikerkk.aicommerce.product_service.service.ProductService;
import com.strikerkk.aicommerce.product_service.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final ProductImageService productImageService;

    // Products API

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create")
    ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {

        ProductResponse productResponse = productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", productResponse));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/update/{productId}")
    ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@Valid @RequestBody ProductRequest request,
                                                               @PathVariable Long productId) {

        ProductResponse updatedProductResponse = productService.updateProduct(request, productId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product updated successfully", updatedProductResponse));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete/{productId}")
    ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long productId) {

        productService.deleteProduct(productId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Product deleted successfully"));
    }


    // Product variant API

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{productId}/add/variants")
    ResponseEntity<ApiResponse<ProductVariantResponse>> createProductVariant(@Valid @RequestBody ProductVariantRequest request,
                                                                             @PathVariable Long productId) {

        ProductVariantResponse productVariantResponse = productVariantService.createProductVariant(request, productId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product variant created successfully", productVariantResponse));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{productId}/update/variants/{variantId}")
    ResponseEntity<ApiResponse<ProductVariantResponse>> updateProductVariant(@Valid @RequestBody ProductVariantRequest request,
                                                                             @PathVariable Long productId,
                                                                             @PathVariable Long variantId) {

        ProductVariantResponse productVariantResponse = productVariantService.updateProductVariant(request, productId, variantId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product variant updated successfully", productVariantResponse));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{productId}/delete/variants/{variantId}")
    ResponseEntity<ApiResponse<Void>> deleteProductVariant(@PathVariable Long productId,
                                                           @PathVariable Long variantId) {

        productVariantService.deleteProductVariant(productId, variantId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Product variant deleted successfully"));
    }


    // Product image API

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{productId}/add/images")
    ResponseEntity<ApiResponse<ProductImageResponse>> createProductImage(@Valid @RequestBody ProductImageRequest request,
                                                                         @PathVariable Long productId) {

        ProductImageResponse productImageResponse = productImageService.addProductImage(request, productId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product image created successfully", productImageResponse));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{productId}/update/images/{imageId}/primaryImage")
    ResponseEntity<ApiResponse<ProductImageResponse>> updateProductImage(@Valid @RequestBody ProductImageRequest request,
                                                                         @PathVariable Long productId,
                                                                         @PathVariable Long imageId) {

        ProductImageResponse productImageResponse = productImageService.updateProductImage(request, productId, imageId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product image updated successfully", productImageResponse));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{productId}/delete/images/{imageId}")
    ResponseEntity<ApiResponse<Void>> deleteProductImage(@PathVariable Long productId,
                                                         @PathVariable Long imageId) {

        productImageService.deleteProductImage(productId, imageId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product image deleted successfully"));
    }
}
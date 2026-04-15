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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;
    private final ProductVariantService productVariantService;
    private final ProductImageService productImageService;

    // Products API

    @PostMapping("/create")
    ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request,
                                                               @RequestHeader("X-user-id") String userId) {

        ProductResponse productResponse = productService.createProduct(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", productResponse));
    }

    @PutMapping("/update/{productId}")
    ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@Valid @RequestBody ProductRequest request,
                                                               @PathVariable Long productId,
                                                               @RequestHeader("X-user-id") String userId) {

        ProductResponse updatedProductResponse = productService.updateProduct(request, productId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product updated successfully", updatedProductResponse));
    }

    @DeleteMapping("/delete/{productId}")
    ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long productId,
                                                    @RequestHeader("X-user-id") String userId) {

        productService.deleteProduct(productId, userId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Product deleted successfully"));
    }


    // Product variant API

    @PostMapping("/{productId}/add/variants")
    ResponseEntity<ApiResponse<ProductVariantResponse>> createProductVariant(@Valid @RequestBody ProductVariantRequest request,
                                                                             @PathVariable Long productId) {

        ProductVariantResponse productVariantResponse = productVariantService.createProductVariant(request, productId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product variant created successfully", productVariantResponse));
    }

    @PutMapping("/{productId}/update/variants/{variantId}")
    ResponseEntity<ApiResponse<ProductVariantResponse>> updateProductVariant(@Valid @RequestBody ProductVariantRequest request,
                                                                             @PathVariable Long productId,
                                                                             @PathVariable Long variantId,
                                                                             @RequestHeader("X-user-id") String userId) {

        ProductVariantResponse productVariantResponse = productVariantService.updateProductVariant(request, productId, variantId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product variant updated successfully", productVariantResponse));
    }

    @DeleteMapping("/{productId}/delete/variants/{variantId}")
    ResponseEntity<ApiResponse<Void>> deleteProductVariant(@PathVariable Long productId,
                                                           @PathVariable Long variantId,
                                                           @RequestHeader("X-user-id") String userId) {

        productVariantService.deleteProductVariant(productId, variantId, userId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Product variant deleted successfully"));
    }


    // Product image API

    @PostMapping("/{productId}/add/images")
    ResponseEntity<ApiResponse<ProductImageResponse>> createProductImage(@Valid @RequestBody ProductImageRequest request,
                                                                         @PathVariable Long productId) {

        ProductImageResponse productImageResponse = productImageService.addProductImage(request, productId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product image created successfully", productImageResponse));
    }

    @PutMapping("/{productId}/update/images/{imageId}/primaryImage")
    ResponseEntity<ApiResponse<ProductImageResponse>> updateProductImage(@Valid @RequestBody ProductImageRequest request,
                                                                         @PathVariable Long productId,
                                                                         @PathVariable Long imageId,
                                                                         @RequestHeader("X-user-id") String userId) {

        ProductImageResponse productImageResponse = productImageService.updateProductImage(request, productId, imageId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product image updated successfully", productImageResponse));
    }

    @DeleteMapping("/{productId}/delete/images/{imageId}")
    ResponseEntity<ApiResponse<Void>> deleteProductImage(@PathVariable Long productId,
                                                         @PathVariable Long imageId,
                                                         @RequestHeader("X-user-id") String userId) {

        productImageService.deleteProductImage(productId, imageId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Product image deleted successfully"));
    }

}

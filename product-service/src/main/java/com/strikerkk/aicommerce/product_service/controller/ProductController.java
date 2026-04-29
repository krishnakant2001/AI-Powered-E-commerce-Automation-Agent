package com.strikerkk.aicommerce.product_service.controller;

import com.strikerkk.aicommerce.product_service.common.ApiResponse;
import com.strikerkk.aicommerce.product_service.dto.clientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.service.ProductCartService;
import com.strikerkk.aicommerce.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCartService productCartService;

    @GetMapping("/all")
    ResponseEntity<ApiResponse<List<ProductResponse>>> allProducts() {

        List<ProductResponse> productResponseList = productService.getAllProducts();

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch all products", productResponseList));
    }


    @GetMapping("/details/{productId}")
    ResponseEntity<ApiResponse<ProductResponse>> productDetails(@PathVariable Long productId) {

        ProductResponse productResponse = productService.getProductDetails(productId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully the products details", productResponse));
    }


    @GetMapping("/{productId}/variants/{variantId}/item-info")
    ProductItemResponse getProductItemDetails(@PathVariable Long productId, @PathVariable Long variantId) {
        return productCartService.getProductItemDetails(productId, variantId);
    }
}

package com.strikerkk.aicommerce.product_service.controller;

import com.strikerkk.aicommerce.product_service.common.ApiResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    @GetMapping("/all")
    ResponseEntity<ApiResponse<List<ProductResponse>>> allProducts(@RequestHeader("X-user-id") String userId) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch all products"));
    }

    @GetMapping("details/{productId}")
    ResponseEntity<ApiResponse<ProductResponse>> productDetails(
            @PathVariable Long productId, @RequestHeader("X-user-id") String userId) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully the products details"));
    }
}

package com.strikerkk.aicommerce.cart_service.controller;

import com.strikerkk.aicommerce.cart_service.common.ApiResponse;
import com.strikerkk.aicommerce.cart_service.dto.request.AddCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.request.UpdateCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.response.CartResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {

    @GetMapping("/")
    ResponseEntity<ApiResponse<CartResponse>> allItemsInCart() {

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch all items of user cart"));
    }

    @PostMapping("/items")
    ResponseEntity<ApiResponse<CartResponse>> addItemInCart(@RequestBody @Valid AddCartItemRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Successfully add item in the cart"));
    }

    @PatchMapping("/items/{cartItemId}")
    ResponseEntity<ApiResponse<CartResponse>> updatedItemInCart(@RequestBody @Valid UpdateCartItemRequest request,
                                                                @PathVariable Long cartItemId) {

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully updated the item in cart"));
    }

    @DeleteMapping("/items/{cartItemId}")
    ResponseEntity<ApiResponse<Void>> deleteItemFromCart(@PathVariable Long cartItemId) {

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Successfully deleted the item from cart"));
    }


    @DeleteMapping("/clear")
    ResponseEntity<ApiResponse<Void>> clearAllItemsFromCart() {

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Successfully clear the cart"));
    }
}

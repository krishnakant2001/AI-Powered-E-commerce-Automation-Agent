package com.strikerkk.aicommerce.cart_service.controller;

import com.strikerkk.aicommerce.cart_service.common.ApiResponse;
import com.strikerkk.aicommerce.cart_service.dto.request.AddCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.request.UpdateCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.response.CartItemResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.CartResponse;
import com.strikerkk.aicommerce.cart_service.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("")
    ResponseEntity<ApiResponse<CartResponse>> allCartItems() {

        CartResponse cartResponse = cartService.allCartItems();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully fetch all items of user cart", cartResponse));
    }

    @GetMapping("/items")
    List<CartItemResponse> getCartItems() {
        CartResponse cartResponse = cartService.allCartItems();
        return cartResponse.getItems();
    }

    @PostMapping("/items")
    ResponseEntity<ApiResponse<CartResponse>> addItemToCart(@RequestBody @Valid AddCartItemRequest request) {

        CartResponse cartResponse = cartService.addItemToCart(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Successfully add item in the cart", cartResponse));
    }

    @PatchMapping("/items/{cartItemId}")
    ResponseEntity<ApiResponse<CartItemResponse>> updateCartItem(@RequestBody @Valid UpdateCartItemRequest request,
                                                                @PathVariable Long cartItemId) {

        CartItemResponse cartItemResponse = cartService.updateCartItem(request, cartItemId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully updated the item in cart", cartItemResponse));
    }

    @DeleteMapping("/items/{cartItemId}")
    ResponseEntity<ApiResponse<Void>> deleteItemFromCart(@PathVariable Long cartItemId) {

        cartService.deleteItemFromCart(cartItemId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully deleted the item from cart"));
    }


    @DeleteMapping("/clear")
    ResponseEntity<ApiResponse<Void>> clearCart() {

        cartService.clearCart();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Successfully clear the cart"));
    }
}

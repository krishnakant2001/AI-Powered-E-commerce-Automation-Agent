package com.strikerkk.aicommerce.cart_service.service;

import com.strikerkk.aicommerce.cart_service.auth.UserContext;
import com.strikerkk.aicommerce.cart_service.clients.ProductClient;
import com.strikerkk.aicommerce.cart_service.dto.request.AddCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.request.UpdateCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.response.CartItemResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.CartResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import com.strikerkk.aicommerce.cart_service.entity.Cart;
import com.strikerkk.aicommerce.cart_service.entity.CartItem;
import com.strikerkk.aicommerce.cart_service.exception.IllegalStateException;
import com.strikerkk.aicommerce.cart_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.cart_service.repository.CartItemRepository;
import com.strikerkk.aicommerce.cart_service.repository.CartRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductClient productClient;
    private final ModelMapper modelMapper;

    public CartResponse allCartItems() {
        Long userId = Long.valueOf(UserContext.getUserId());

        Cart cart = getOrCreateCart(userId);
        syncCartWithLatestProductData(cart);

        return modelMapper.map(cart, CartResponse.class);
    }

    public CartResponse addItemToCart(AddCartItemRequest request) {
        Long userId = Long.valueOf(UserContext.getUserId());

        Cart cart = getOrCreateCart(userId);

        Optional<CartItem> existingItemOpt = cartItemRepository
                .findByCartIdAndProductIdAndVariantId(cart.getId(), request.getProductId(), request.getVariantId());

        if(existingItemOpt.isPresent()) {
            updateExistingItem(existingItemOpt.get(), request.getQuantity());
        }
        else {
            ProductCartResponse response = productClient.getProductCartInfo(request.getProductId(), request.getVariantId());

            if(!response.getInStock()) {
                throw new IllegalStateException("Product variant is out of stock. productId=" + request.getProductId()
                        + ", variantId=" + request.getVariantId());
            }

            // Adding new item in Cart
            CartItem newCartItem = CartItem.builder()
                    .cart(cart)
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .quantity(request.getQuantity())
                    .productName(response.getProductName())
                    .priceAtAdd(response.getPrice())
                    .size(response.getSize())
                    .color(response.getColor())
                    .imageUrl(response.getImageUrl())
                    .build();

            cart.getCartItems().add(newCartItem);
            cartRepository.save(cart);
        }

        syncCartWithLatestProductData(cart);

        return modelMapper.map(cart, CartResponse.class);
    }

    public CartItemResponse updateCartItem(@Valid UpdateCartItemRequest request, Long cartItemId) {

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart Item is not found"));


        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);

        return modelMapper.map(cartItem, CartItemResponse.class);
    }



    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("No cart found for userId {} - Creating a new cart", userId);
                    Cart newCart = Cart.builder()
                            .userId(userId)
                            .build();

                    return cartRepository.save(newCart);
                });
    }

    private void updateExistingItem(CartItem item, int quantity) {
        item.setQuantity(item.getQuantity() + quantity);
        cartItemRepository.save(item);
    }

    private void syncCartWithLatestProductData(Cart cart) {
        for(CartItem item : cart.getCartItems()) {
            ProductCartResponse response = productClient.getProductCartInfo(item.getProductId(), item.getVariantId());

            if(!response.getIsAvailable()) {
                cartItemRepository.delete(item);
                continue;
            }

            if(!item.getPriceAtAdd().equals(response.getPrice())) {
                item.setPriceAtAdd(response.getPrice());
            }
        }
    }

}

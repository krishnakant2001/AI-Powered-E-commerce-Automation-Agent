package com.strikerkk.aicommerce.cart_service.service;

import com.strikerkk.aicommerce.cart_service.auth.UserContext;
import com.strikerkk.aicommerce.cart_service.clients.ProductClient;
import com.strikerkk.aicommerce.cart_service.dto.request.AddCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.request.UpdateCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.response.CartItemResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.CartResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductDetails;
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

import java.math.BigDecimal;
import java.util.List;
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

        boolean isEnriched = syncCartWithLatestProductData(cart);

        return mapToCartResponse(cart, isEnriched);
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
                    .productBrand(response.getBrandName())
                    .priceAtAdd(response.getPrice())
                    .size(response.getSize())
                    .color(response.getColor())
                    .ProductImageUrl(response.getImageUrl())
                    .build();

            cart.getCartItems().add(newCartItem);
            cartRepository.save(cart);
        }

        return mapToCartResponse(cart);
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

    private boolean syncCartWithLatestProductData(Cart cart) {

        boolean isUpdated = false;

        for(CartItem item : cart.getCartItems()) {
            ProductCartResponse response = productClient.getProductCartInfo(item.getProductId(), item.getVariantId());

            if(!response.getIsAvailable()) {
                cartItemRepository.delete(item);
                isUpdated = true;
                continue;
            }

            if(!item.getPriceAtAdd().equals(response.getPrice())) {
                item.setPriceAtAdd(response.getPrice());
                isUpdated = true;
            }
        }
        return isUpdated;
    }

    private ProductDetails mapToProductDetails(CartItem cartItem) {
        ProductDetails details = modelMapper.map(cartItem, ProductDetails.class);
        details.setVariantName(cartItem.getSize() + " - " + cartItem.getColor());
        return details;
    }

    private CartResponse mapToCartResponse(Cart cart) {
        return mapToCartResponse(cart, false);
    }

    private CartResponse mapToCartResponse(Cart cart, boolean isEnriched) {

        List<CartItemResponse> items = cart.getCartItems()
                .stream()
                .map(cartItem -> {
                    CartItemResponse itemResponse = modelMapper.map(cartItem, CartItemResponse.class);

                    BigDecimal itemTotal = cartItem.getPriceAtAdd().multiply(BigDecimal.valueOf(cartItem.getQuantity()));

                    itemResponse.setItemTotal(itemTotal);
                    itemResponse.setProductDetails(mapToProductDetails(cartItem));

                    return itemResponse;
                })
                .toList();


        BigDecimal totalAmount = items.stream()
                .map(CartItemResponse::getItemTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Integer totalItems = items.stream()
                .mapToInt(CartItemResponse::getQuantity)
                .sum();

        CartResponse response = new CartResponse();
        response.setCartId(cart.getId());
        response.setUserId(cart.getUserId());
        response.setItems(items);
        response.setTotalAmount(totalAmount);
        response.setTotalItems(totalItems);
        response.setTotalUniqueItems(items.size());
        response.setEnriched(isEnriched);
        response.setCreatedAt(cart.getCreatedAt());
        response.setUpdatedAt(cart.getUpdatedAt());

        return response;
    }

}

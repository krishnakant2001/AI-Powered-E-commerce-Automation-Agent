package com.strikerkk.aicommerce.cart_service.support;

import com.strikerkk.aicommerce.cart_service.dto.request.AddCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.request.UpdateCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductCartResponse;
import com.strikerkk.aicommerce.cart_service.entity.Cart;
import com.strikerkk.aicommerce.cart_service.entity.CartItem;

import java.math.BigDecimal;
import java.util.ArrayList;

public final class TestDataFactory {

    public static final Long USER_ID = 42L;
    public static final Long OTHER_USER_ID = 99L;
    public static final Long CART_ID = 7L;
    public static final Long CART_ITEM_ID = 500L;
    public static final Long PRODUCT_ID = 1L;
    public static final Long VARIANT_ID = 2L;

    public static final String PRODUCT_NAME = "JBL Flip 6";
    public static final String BRAND = "JBL";
    public static final String IMAGE_URL = "products/1/primary.png";
    public static final String SIZE = "M";
    public static final String COLOR = "Black";

    public static final BigDecimal PRICE = new BigDecimal("8999.00");

    private TestDataFactory() {
    }

    // entities ------------------------------------------------------------------

    /** A persisted-looking, empty cart owned by {@link #USER_ID}. */
    public static Cart cart() {
        return cart(CART_ID, USER_ID);
    }

    public static Cart cart(Long id, Long userId) {
        return Cart.builder()
                .id(id)
                .userId(userId)
                .cartItems(new ArrayList<>())
                .build();
    }

    /** A cart item that is NOT attached to any cart - handy for the pure mapping tests. */
    public static CartItem cartItem() {
        return cartItem(CART_ITEM_ID, null, PRODUCT_ID, VARIANT_ID, 1, PRICE);
    }

    /** A cart item attached to {@code cart}, but NOT yet added to {@code cart.getCartItems()}. */
    public static CartItem cartItem(Cart cart, int quantity) {
        return cartItem(CART_ITEM_ID, cart, PRODUCT_ID, VARIANT_ID, quantity, PRICE);
    }

    public static CartItem cartItem(Long id,
                                    Cart cart,
                                    Long productId,
                                    Long variantId,
                                    int quantity,
                                    BigDecimal priceAtAdd) {
        return CartItem.builder()
                .id(id)
                .cart(cart)
                .productId(productId)
                .variantId(variantId)
                .quantity(quantity)
                .productName(PRODUCT_NAME)
                .productBrand(BRAND)
                .priceAtAdd(priceAtAdd)
                .ProductImageUrl(IMAGE_URL)
                .size(SIZE)
                .color(COLOR)
                .build();
    }

    /** Attaches a brand new item to {@code cart} on both sides of the association. */
    public static CartItem attachItem(Cart cart, Long id, Long productId, Long variantId, int quantity, BigDecimal price) {
        CartItem item = cartItem(id, cart, productId, variantId, quantity, price);
        cart.getCartItems().add(item);
        return item;
    }

    // client payloads ------------------------------------------------------------------

    /** The payload product-service returns for {@code /products/{id}/variants/{id}/item-info}. */
    public static ProductCartResponse productCartResponse() {
        return productCartResponse(PRICE, true, true);
    }

    public static ProductCartResponse productCartResponse(BigDecimal price, boolean available, boolean inStock) {
        ProductCartResponse response = new ProductCartResponse();
        response.setProductId(PRODUCT_ID);
        response.setProductName(PRODUCT_NAME);
        response.setBrandName(BRAND);
        response.setPrice(price);
        response.setIsAvailable(available);
        response.setVariantId(VARIANT_ID);
        response.setSize(SIZE);
        response.setColor(COLOR);
        response.setInStock(inStock);
        response.setImageUrl(IMAGE_URL);
        return response;
    }

    // requests ------------------------------------------------------------------

    public static AddCartItemRequest addRequest() {
        return addRequest(PRODUCT_ID, VARIANT_ID, 1);
    }

    public static AddCartItemRequest addRequest(Long productId, Long variantId, Integer quantity) {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(productId);
        request.setVariantId(variantId);
        request.setQuantity(quantity);
        return request;
    }

    public static UpdateCartItemRequest updateRequest(Integer quantity) {
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setQuantity(quantity);
        return request;
    }
}


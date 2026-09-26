package com.strikerkk.aicommerce.order_service.support;

import com.strikerkk.aicommerce.order_service.dto.ClientResponse.AddressResponse;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.CartItemResponse;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductDetails;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.order_service.dto.request.PlaceOrderRequest;
import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.entity.OrderItem;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.payment_service.event.PaymentSuccessEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TestDataFactory {

    public static final Long USER_ID = 42L;
    public static final Long OTHER_USER_ID = 99L;
    public static final Long ORDER_ID = 100L;
    public static final Long ORDER_ITEM_ID = 500L;
    public static final Long ADDRESS_ID = 7L;
    public static final Long PRODUCT_ID = 1L;
    public static final Long VARIANT_ID = 2L;

    public static final String PRODUCT_NAME = "JBL Flip 6";
    public static final String BRAND = "JBL";
    public static final String IMAGE_URL = "products/1/primary.png";
    public static final String SIZE = "M";
    public static final String COLOR = "Black";

    /** Above the free delivery threshold of 1999. */
    public static final BigDecimal PRICE = new BigDecimal("8999.00");
    /** Below the free delivery threshold of 1999. */
    public static final BigDecimal CHEAP_PRICE = new BigDecimal("499.00");
    /** Exactly the threshold - the comparison is strictly greater than, so this one still pays. */
    public static final BigDecimal THRESHOLD_PRICE = new BigDecimal("1999");

    public static final BigDecimal PAID_DELIVERY = BigDecimal.valueOf(40);
    public static final BigDecimal FREE_DELIVERY = BigDecimal.valueOf(0);

    public static final String FORMATTED_ADDRESS = "12B MG Road Bengaluru Karnataka India 560001";

    private TestDataFactory() {
    }

    // entities ------------------------------------------------------------------

    /** A pending order of {@link #USER_ID} carrying a single {@link #PRICE} item. */
    public static Order order() {
        return order(ORDER_ID, USER_ID, OrderStatus.PENDING);
    }

    public static Order order(Long id, Long userId, OrderStatus status) {
        Order order = Order.builder()
                .id(id)
                .userId(userId)
                .addressId(ADDRESS_ID)
                .totalAmount(PRICE)
                .deliveryCharges(FREE_DELIVERY)
                .needToPay(PRICE)
                .status(status)
                .orderItems(new ArrayList<>())
                .build();

        attachItem(order, ORDER_ITEM_ID, PRODUCT_ID, VARIANT_ID, 1, PRICE);
        return order;
    }

    /** An order of {@link #USER_ID} without any line - used to prove the null guards. */
    public static Order emptyOrder(Long id, OrderStatus status) {
        return Order.builder()
                .id(id)
                .userId(USER_ID)
                .addressId(ADDRESS_ID)
                .totalAmount(BigDecimal.ZERO)
                .deliveryCharges(PAID_DELIVERY)
                .needToPay(PAID_DELIVERY)
                .status(status)
                .orderItems(new ArrayList<>())
                .build();
    }

    /** A line that is not attached to any order - handy for the pure mapping tests. */
    public static OrderItem orderItem() {
        return orderItem(ORDER_ITEM_ID, null, PRODUCT_ID, VARIANT_ID, 1, PRICE);
    }

    public static OrderItem orderItem(Long id,
                                      Order order,
                                      Long productId,
                                      Long variantId,
                                      int quantity,
                                      BigDecimal priceAtOrder) {
        return OrderItem.builder()
                .id(id)
                .order(order)
                .productId(productId)
                .variantId(variantId)
                .quantity(quantity)
                .productName(PRODUCT_NAME)
                .productBrand(BRAND)
                .productImageUrl(IMAGE_URL)
                .size(SIZE)
                .color(COLOR)
                .priceAtOrder(priceAtOrder)
                .lineTotal(priceAtOrder.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    /** Attaches a brand-new line to {@code order} on both sides of the association. */
    public static OrderItem attachItem(Order order,
                                       Long id,
                                       Long productId,
                                       Long variantId,
                                       int quantity,
                                       BigDecimal priceAtOrder) {
        OrderItem item = orderItem(id, order, productId, variantId, quantity, priceAtOrder);
        order.getOrderItems().add(item);
        return item;
    }

    // client payloads ------------------------------------------------------------------

    /** What cart-service answers for {@code GET /cart/items}. */
    public static CartItemResponse cartItemResponse() {
        return cartItemResponse(PRODUCT_ID, VARIANT_ID, 1, PRICE);
    }

    public static CartItemResponse cartItemResponse(Long productId,
                                                    Long variantId,
                                                    Integer quantity,
                                                    BigDecimal priceAtAdd) {
        CartItemResponse response = new CartItemResponse();
        response.setId(productId);
        response.setProductId(productId);
        response.setVariantId(variantId);
        response.setQuantity(quantity);
        response.setPriceAtAdd(priceAtAdd);
        response.setItemTotal(priceAtAdd.multiply(BigDecimal.valueOf(quantity)));
        response.setProductDetails(productDetails());
        return response;
    }

    public static List<CartItemResponse> cartItems(CartItemResponse... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    public static ProductDetails productDetails() {
        ProductDetails details = new ProductDetails();
        details.setProductName(PRODUCT_NAME);
        details.setProductBrand(BRAND);
        details.setProductImageUrl(IMAGE_URL);
        details.setVariantName(SIZE + " - " + COLOR);
        details.setSize(SIZE);
        details.setColor(COLOR);
        details.setVariantSku("JBL-FLIP6-M-BLK");
        return details;
    }

    /** What product-service answers for {@code GET /products/{id}/variants/{id}/item-info}. */
    public static ProductItemResponse productItemResponse() {
        return productItemResponse(PRICE);
    }

    public static ProductItemResponse productItemResponse(BigDecimal price) {
        ProductItemResponse response = new ProductItemResponse();
        response.setProductId(PRODUCT_ID);
        response.setProductName(PRODUCT_NAME);
        response.setBrandName(BRAND);
        response.setPrice(price);
        response.setIsAvailable(true);
        response.setVariantId(VARIANT_ID);
        response.setSize(SIZE);
        response.setColor(COLOR);
        response.setInStock(true);
        response.setImageUrl(IMAGE_URL);
        return response;
    }

    /** What user-service answers for {@code GET /users/address/{addressId}}. */
    public static AddressResponse addressResponse() {
        AddressResponse address = new AddressResponse();
        address.setHouseNo("12B");
        address.setStreet("MG Road");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setCountry("India");
        address.setPinCode("560001");
        address.setIsDefault(true);
        return address;
    }

    // requests and events ------------------------------------------------------------------

    public static PlaceOrderRequest placeOrderRequest() {
        return placeOrderRequest(USER_ID, ADDRESS_ID, PRODUCT_ID, VARIANT_ID);
    }

    public static PlaceOrderRequest placeOrderRequest(Long userId,
                                                      Long addressId,
                                                      Long productId,
                                                      Long variantId) {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setUserId(userId);
        request.setAddressId(addressId);
        request.setProductId(productId);
        request.setVariantId(variantId);
        return request;
    }

    public static PaymentSuccessEvent paymentSuccessEvent(Long orderId, String paymentStatus) {
        PaymentSuccessEvent event = new PaymentSuccessEvent();
        event.setId(1L);
        event.setUserId(USER_ID);
        event.setOrderId(orderId);
        event.setPaymentStatus(paymentStatus);
        return event;
    }
}


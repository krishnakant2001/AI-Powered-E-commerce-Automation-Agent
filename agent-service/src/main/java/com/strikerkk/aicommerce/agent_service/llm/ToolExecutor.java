package com.strikerkk.aicommerce.agent_service.llm;

import com.strikerkk.aicommerce.agent_service.clients.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolExecutor {

    private final ProductServiceClient productServiceClient;
    private final CartServiceClient cartServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final UserServiceClient userServiceClient;

    // ——————————————————————————————————
    // Product Tools
    // ——————————————————————————————————

    public String searchProducts(String search, String category) {
        log.info("Tool: searchProducts search={} category={}", search, category);
        return productServiceClient.getAllProducts(search, category, 0, 10);
    }

    public String getProductDetails(Long productId) {
        log.info("Tool: getProductDetails productId={}", productId);
        return productServiceClient.getProductDetails(productId);
    }

    public String getProductItemDetails(Long productId, Long variantId) {
        log.info("Tool: getProductItemDetails with variant productId={} variantId={}", productId, variantId);
        return productServiceClient.getProductItemDetails(productId, variantId);
    }


    // ——————————————————————————————————
    // Cart Tools
    // ——————————————————————————————————

    public String getCart() {
        log.info("Tool: getCartItems");
        return cartServiceClient.allCartItems();
    }

    public String addToCart(String requestBody) {
        log.info("Tool: addToCart body={}", requestBody);
        return cartServiceClient.addItemToCart(requestBody);
    }

    public String clearCart() {
        log.info("Tool: clearCart");
        return cartServiceClient.clearCart();
    }


    // ——————————————————————————————————
    // Order Tools
    // ——————————————————————————————————

    public String placeOrder(String requestBody) {
        log.info("Tool: placeOrder body={}", requestBody);
        return orderServiceClient.placeOrder(requestBody);
    }

    public String buyNow(String requestBody) {
        log.info("Tool: buyNow body={}", requestBody);
        return orderServiceClient.buyNow(requestBody);
    }

    public String getOrder(Long orderId) {
        log.info("Tool: getOrder order={}", orderId);
        return orderServiceClient.getOrderById(orderId);
    }

    public String getMyOrders() {
        log.info("Tool: getMyOrders");
        return orderServiceClient.getMyOrders();
    }


    // ——————————————————————————————————
    // Payment Tools
    // ——————————————————————————————————

    public String initiatePayment(String requestBody) {
        log.info("Tool: initiatePayment body={}", requestBody);
        return paymentServiceClient.initiatePayment(requestBody);
    }


    // ——————————————————————————————————
    // User / Address Tools
    // ——————————————————————————————————

    public String getAllAddresses() {
        log.info("Tool: getAllAddresses");
        return userServiceClient.getAllAddresses();
    }


    // ——————————————————————————————————
    // Fallback Error
    // ——————————————————————————————————

    public String searchProductsFallback(String search, String category, Exception ex) {
        log.error("searchProducts fallback triggered: {}", ex.getMessage());
        return buildErrorJson("searchProducts",
                "Product search is temporarily unavailable. Please try again in a moment.");
    }

    public String getProductDetailsFallback(Long productId, Exception ex) {
        log.error("getProductDetails fallback triggered for productId={}: {}", productId, ex.getMessage());
        return buildErrorJson("getProductDetails",
                "Unable to fetch product details right now. Please try again.");
    }

    public String getProductItemDetailsFallback(Long productId, Long variantId, Exception ex) {
        log.error("getProductItemDetails fallback triggered: {}", ex.getMessage());
        return buildErrorJson("getVariantInfo",
                "Unable to check stock availability right now. Please try again.");
    }

    public String getCartFallback(Exception ex) {
        log.error("getCart fallback triggered: {}", ex.getMessage());
        return buildErrorJson("getCart",
                "Unable to fetch cart items right now. Please try again.");
    }

    public String addToCartFallback(String requestBody, Exception ex) {
        log.error("addToCart fallback triggered: {}", ex.getMessage());
        return buildErrorJson("addToCart",
                "Unable to add item to cart right now. Please try again.");
    }

    public String clearCartFallback(Exception ex) {
        log.error("clearCart fallback triggered: {}", ex.getMessage());
        return buildErrorJson("clearCart",
                "Unable to clear cart right now. Please try again.");
    }

    public String placeOrderFallback(String requestBody, Exception ex) {
        log.error("placeOrder fallback triggered: {}", ex.getMessage());
        return buildErrorJson("placeOrder",
                "Unable to place your order right now. Please try again in a moment.");
    };

    public String buyNowFallback(String requestBody, Exception ex) {
        log.error("buyNow fallback triggered: {}", ex.getMessage());
        return buildErrorJson("buyNow",
                "Unable to place your order right now. Please try again in a moment.");
    };

    public String getOrderFallback(Long orderId, Exception ex) {
        log.error("getOrder fallback triggered for orderId={}: {}", orderId, ex.getMessage());
        return buildErrorJson("getOrder",
                "Unable to fetch order details right now. Please try again.");
    };

    public String getMyOrdersFallback(Exception ex) {
        log.error("getMyOrders fallback triggered: {}", ex.getMessage());
        return buildErrorJson("getMyOrders",
                "Unable to fetch your orders right now. Please try again.");
    };

    public String initiatePaymentFallback(String requestBody, Exception ex) {
        log.error("initiatePayment fallback triggered: {}", ex.getMessage());
        return buildErrorJson("initiatePayment",
                "Payment service is temporarily unavailable. Your order has been saved. Please try payment again.");
    }

    public String getAllAddressesFallback(Exception ex) {
        log.error("getAllAddresses fallback triggered: {}", ex.getMessage());
        return buildErrorJson("getAllAddresses",
                "Unable to fetch your addresses right now. Please try again.");
    }


    // Error response
    private String buildErrorJson(String toolName, String message) {
        return String.format(
                "{\"error\": true, \"tool\": \"%s\", \"message\": \"%s\"}",
                toolName, message
        );
    }
}

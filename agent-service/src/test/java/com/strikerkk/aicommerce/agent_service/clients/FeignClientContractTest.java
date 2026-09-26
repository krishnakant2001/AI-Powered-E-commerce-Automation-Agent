package com.strikerkk.aicommerce.agent_service.clients;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Feign clients")
class FeignClientContractTest {

    private Method method(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError(name + " is missing from " + type.getSimpleName(), ex);
        }
    }

    private void assertIsFeignClient(Class<?> type, String serviceName, String path) {
        assertThat(type.isInterface()).as("%s must be an interface", type.getSimpleName()).isTrue();

        FeignClient annotation = type.getAnnotation(FeignClient.class);

        assertThat(annotation).as("%s must be a @FeignClient", type.getSimpleName()).isNotNull();
        assertThat(annotation.name()).isEqualTo(serviceName);
        assertThat(annotation.path()).isEqualTo(path);
    }

    private void assertEveryMethodReturnsRawJson(Class<?> type) {
        assertThat(type.getMethods())
                .as("the agent hands raw JSON straight back to the LLM")
                .allMatch(method -> method.getReturnType() == String.class);
    }

    @Nested
    @DisplayName("ProductServiceClient")
    class ProductServiceClientTest {

        @Test
        @DisplayName("points at product-service under /products")
        void pointsAtProductService() {
            assertIsFeignClient(ProductServiceClient.class, "product-service", "/products");
            assertEveryMethodReturnsRawJson(ProductServiceClient.class);
        }

        @Test
        @DisplayName("search is a GET /all with four optional query parameters")
        void searchIsAGet() {
            Method method = method(ProductServiceClient.class, "getAllProducts",
                    String.class, String.class, Integer.class, Integer.class);

            assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/all");
            assertThat(method.getParameters())
                    .allMatch(parameter -> parameter.getAnnotation(RequestParam.class) != null)
                    .allMatch(parameter -> !parameter.getAnnotation(RequestParam.class).required());
        }

        @Test
        @DisplayName("product details is a GET /details/{productId}")
        void productDetailsIsAGet() {
            Method method = method(ProductServiceClient.class, "getProductDetails", Long.class);

            assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/details/{productId}");
            assertThat(method.getParameters()[0].getAnnotation(PathVariable.class)).isNotNull();
        }

        @Test
        @DisplayName("variant info is a GET on the nested variant path")
        void variantInfoIsAGet() {
            Method method = method(ProductServiceClient.class, "getProductItemDetails", Long.class, Long.class);

            assertThat(method.getAnnotation(GetMapping.class).value())
                    .containsExactly("/{productId}/variants/{variantId}/item-info");
            assertThat(method.getParameters())
                    .allMatch(parameter -> parameter.getAnnotation(PathVariable.class) != null);
        }
    }

    @Nested
    @DisplayName("CartServiceClient")
    class CartServiceClientTest {

        @Test
        @DisplayName("points at cart-service under /cart")
        void pointsAtCartService() {
            assertIsFeignClient(CartServiceClient.class, "cart-service", "/cart");
            assertEveryMethodReturnsRawJson(CartServiceClient.class);
        }

        @Test
        @DisplayName("reading the cart is a GET on the root path")
        void readingTheCartIsAGet() {
            assertThat(method(CartServiceClient.class, "allCartItems").getAnnotation(GetMapping.class).value())
                    .containsExactly("");
        }

        @Test
        @DisplayName("adding an item POSTs a JSON body to /items")
        void addingAnItemPostsJson() {
            PostMapping mapping = method(CartServiceClient.class, "addItemToCart", String.class)
                    .getAnnotation(PostMapping.class);

            assertThat(mapping.value()).containsExactly("/items");
            assertThat(mapping.consumes()).containsExactly("application/json");
        }

        @Test
        @DisplayName("updating an item PATCHes /items/{cartItemId}")
        void updatingAnItemPatches() {
            Method method = method(CartServiceClient.class, "updateCartItem", Long.class, String.class);

            assertThat(method.getAnnotation(PatchMapping.class).value()).containsExactly("/items/{cartItemId}");
            assertThat(method.getParameters()[0].getAnnotation(PathVariable.class)).isNotNull();
        }

        @Test
        @DisplayName("clearing the cart is a DELETE /clear")
        void clearingTheCartIsADelete() {
            assertThat(method(CartServiceClient.class, "clearCart").getAnnotation(DeleteMapping.class).value())
                    .containsExactly("/clear");
        }
    }

    @Nested
    @DisplayName("OrderServiceClient")
    class OrderServiceClientTest {

        @Test
        @DisplayName("points at order-service under /orders")
        void pointsAtOrderService() {
            assertIsFeignClient(OrderServiceClient.class, "order-service", "/orders");
            assertEveryMethodReturnsRawJson(OrderServiceClient.class);
        }

        @Test
        @DisplayName("placing an order POSTs a JSON body to the root path")
        void placingAnOrderPostsJson() {
            PostMapping mapping = method(OrderServiceClient.class, "placeOrder", String.class)
                    .getAnnotation(PostMapping.class);

            assertThat(mapping.value()).containsExactly("");
            assertThat(mapping.consumes()).containsExactly("application/json");
        }

        @Test
        @DisplayName("buy-now has its own POST endpoint")
        void buyNowHasItsOwnEndpoint() {
            assertThat(method(OrderServiceClient.class, "buyNow", String.class)
                    .getAnnotation(PostMapping.class).value())
                    .containsExactly("/buy-now");
        }

        @Test
        @DisplayName("the read endpoints are GETs")
        void theReadEndpointsAreGets() {
            assertThat(method(OrderServiceClient.class, "getOrderById", Long.class)
                    .getAnnotation(GetMapping.class).value()).containsExactly("/{orderId}");
            assertThat(method(OrderServiceClient.class, "getMyOrders")
                    .getAnnotation(GetMapping.class).value()).containsExactly("/my-orders");
            assertThat(method(OrderServiceClient.class, "getOrderItems", Long.class)
                    .getAnnotation(GetMapping.class).value()).containsExactly("/{orderId}/items");
        }
    }

    @Nested
    @DisplayName("PaymentServiceClient")
    class PaymentServiceClientTest {

        @Test
        @DisplayName("points at payment-service under /payments")
        void pointsAtPaymentService() {
            assertIsFeignClient(PaymentServiceClient.class, "payment-service", "/payments");
            assertEveryMethodReturnsRawJson(PaymentServiceClient.class);
        }

        @Test
        @DisplayName("initiating a payment POSTs a JSON body to /initiate")
        void initiatingAPaymentPostsJson() {
            PostMapping mapping = method(PaymentServiceClient.class, "initiatePayment", String.class)
                    .getAnnotation(PostMapping.class);

            assertThat(mapping.value()).containsExactly("/initiate");
            assertThat(mapping.consumes()).containsExactly("application/json");
        }
    }

    @Nested
    @DisplayName("UserServiceClient")
    class UserServiceClientTest {

        @Test
        @DisplayName("points at user-service under /users")
        void pointsAtUserService() {
            assertIsFeignClient(UserServiceClient.class, "user-service", "/users");
            assertEveryMethodReturnsRawJson(UserServiceClient.class);
        }

        @Test
        @DisplayName("listing addresses is a GET /address/all")
        void listingAddressesIsAGet() {
            assertThat(method(UserServiceClient.class, "getAllAddresses")
                    .getAnnotation(GetMapping.class).value())
                    .containsExactly("/address/all");
        }

        @Test
        @DisplayName("fetching one address is a GET /address/{addressId}")
        void fetchingOneAddressIsAGet() {
            assertThat(method(UserServiceClient.class, "getAddressByAddressId", Long.class)
                    .getAnnotation(GetMapping.class).value())
                    .containsExactly("/address/{addressId}");
        }
    }
}


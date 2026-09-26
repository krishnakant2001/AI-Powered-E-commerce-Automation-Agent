package com.strikerkk.aicommerce.agent_service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.agent_service.clients.CartServiceClient;
import com.strikerkk.aicommerce.agent_service.clients.OrderServiceClient;
import com.strikerkk.aicommerce.agent_service.clients.PaymentServiceClient;
import com.strikerkk.aicommerce.agent_service.clients.ProductServiceClient;
import com.strikerkk.aicommerce.agent_service.clients.UserServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolExecutor")
class ToolExecutorTest {

    private static final String INSTANCE = "microservice-call";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private CartServiceClient cartServiceClient;

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private ToolExecutor toolExecutor;

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new AssertionError("the fallback did not produce valid JSON: " + json, ex);
        }
    }

    private void assertIsAnErrorPayload(String payload, String toolName) {
        JsonNode parsed = parse(payload);

        assertThat(parsed.path("error").asBoolean()).as("error flag").isTrue();
        assertThat(parsed.path("tool").asText()).isEqualTo(toolName);
        assertThat(parsed.path("message").asText()).isNotBlank();

        // the orchestrator looks for this exact substring to mark the action FAILED
        assertThat(payload).contains("\"error\": true");
    }

    @Nested
    @DisplayName("Delegation")
    class Delegation {

        @Test
        @DisplayName("searchProducts always asks for the first page of ten")
        void searchProducts() {
            when(productServiceClient.getAllProducts("speaker", "electronics", 0, 10)).thenReturn("[]");

            assertThat(toolExecutor.searchProducts("speaker", "electronics")).isEqualTo("[]");

            verify(productServiceClient).getAllProducts("speaker", "electronics", 0, 10);
        }

        @Test
        @DisplayName("getProductDetails delegates straight to product-service")
        void getProductDetails() {
            when(productServiceClient.getProductDetails(7L)).thenReturn("{\"id\":7}");

            assertThat(toolExecutor.getProductDetails(7L)).isEqualTo("{\"id\":7}");
        }

        @Test
        @DisplayName("getProductItemDetails passes the ids in the right order")
        void getProductItemDetails() {
            when(productServiceClient.getProductItemDetails(7L, 9L)).thenReturn("{\"inStock\":true}");

            assertThat(toolExecutor.getProductItemDetails(7L, 9L)).isEqualTo("{\"inStock\":true}");

            verify(productServiceClient).getProductItemDetails(7L, 9L);
        }

        @Test
        @DisplayName("the cart tools delegate to cart-service")
        void theCartToolsDelegate() {
            when(cartServiceClient.allCartItems()).thenReturn("{\"items\":[]}");
            when(cartServiceClient.addItemToCart("{\"productId\":7}")).thenReturn("{\"cartItemId\":9}");
            when(cartServiceClient.clearCart()).thenReturn("{\"cleared\":true}");

            assertThat(toolExecutor.getCart()).isEqualTo("{\"items\":[]}");
            assertThat(toolExecutor.addToCart("{\"productId\":7}")).isEqualTo("{\"cartItemId\":9}");
            assertThat(toolExecutor.clearCart()).isEqualTo("{\"cleared\":true}");
        }

        @Test
        @DisplayName("the order tools delegate to order-service")
        void theOrderToolsDelegate() {
            when(orderServiceClient.placeOrder("{\"addressId\":5}")).thenReturn("{\"orderId\":1001}");
            when(orderServiceClient.buyNow("{\"productId\":7}")).thenReturn("{\"orderId\":1002}");
            when(orderServiceClient.getOrderById(1001L)).thenReturn("{\"status\":\"CONFIRMED\"}");
            when(orderServiceClient.getMyOrders()).thenReturn("[]");
            when(orderServiceClient.getOrderItems(1001L)).thenReturn("[]");

            assertThat(toolExecutor.placeOrder("{\"addressId\":5}")).isEqualTo("{\"orderId\":1001}");
            assertThat(toolExecutor.buyNow("{\"productId\":7}")).isEqualTo("{\"orderId\":1002}");
            assertThat(toolExecutor.getOrder(1001L)).isEqualTo("{\"status\":\"CONFIRMED\"}");
            assertThat(toolExecutor.getMyOrders()).isEqualTo("[]");
            assertThat(toolExecutor.getOrderItems(1001L)).isEqualTo("[]");
        }

        @Test
        @DisplayName("initiatePayment delegates to payment-service")
        void initiatePayment() {
            when(paymentServiceClient.initiatePayment("{\"orderId\":1001}")).thenReturn("{\"paymentId\":\"pay_1\"}");

            assertThat(toolExecutor.initiatePayment("{\"orderId\":1001}")).isEqualTo("{\"paymentId\":\"pay_1\"}");
        }

        @Test
        @DisplayName("getAllAddresses delegates to user-service")
        void getAllAddresses() {
            when(userServiceClient.getAllAddresses()).thenReturn("[{\"addressId\":5}]");

            assertThat(toolExecutor.getAllAddresses()).isEqualTo("[{\"addressId\":5}]");
        }

        @Test
        @DisplayName("adds no logic of its own - whatever comes back is passed along")
        void addsNoLogicOfItsOwn() {
            when(userServiceClient.getAllAddresses()).thenReturn(null);

            assertThat(toolExecutor.getAllAddresses()).isNull();
        }
    }

    @Nested
    @DisplayName("Fallbacks")
    class Fallbacks {

        private final Exception cause = new IllegalStateException("connection refused");

        @Test
        @DisplayName("every fallback hands the model a JSON error instead of throwing")
        void everyFallbackReturnsJson() {
            assertIsAnErrorPayload(toolExecutor.searchProductsFallback("speaker", null, cause), "searchProducts");
            assertIsAnErrorPayload(toolExecutor.getProductDetailsFallback(7L, cause), "getProductDetails");
            assertIsAnErrorPayload(toolExecutor.getProductItemDetailsFallback(7L, 9L, cause), "getVariantInfo");
            assertIsAnErrorPayload(toolExecutor.getCartFallback(cause), "getCart");
            assertIsAnErrorPayload(toolExecutor.addToCartFallback("{}", cause), "addToCart");
            assertIsAnErrorPayload(toolExecutor.clearCartFallback(cause), "clearCart");
            assertIsAnErrorPayload(toolExecutor.placeOrderFallback("{}", cause), "placeOrder");
            assertIsAnErrorPayload(toolExecutor.buyNowFallback("{}", cause), "buyNow");
            assertIsAnErrorPayload(toolExecutor.getOrderFallback(1001L, cause), "getOrder");
            assertIsAnErrorPayload(toolExecutor.getMyOrdersFallback(cause), "getMyOrders");
            assertIsAnErrorPayload(toolExecutor.getOrderItemsFallback(cause), "getMyOrders");
            assertIsAnErrorPayload(toolExecutor.initiatePaymentFallback("{}", cause), "initiatePayment");
            assertIsAnErrorPayload(toolExecutor.getAllAddressesFallback(cause), "getAllAddresses");
        }

        @Test
        @DisplayName("no fallback ever touches a downstream service")
        void noFallbackTouchesADownstreamService() {
            toolExecutor.searchProductsFallback("speaker", null, cause);
            toolExecutor.placeOrderFallback("{}", cause);
            toolExecutor.initiatePaymentFallback("{}", cause);

            org.mockito.Mockito.verifyNoInteractions(
                    productServiceClient, cartServiceClient, orderServiceClient,
                    paymentServiceClient, userServiceClient);
        }

        @Test
        @DisplayName("the message is written for a shopper, not for a developer")
        void theMessageIsWrittenForAShopper() {
            assertThat(parse(toolExecutor.searchProductsFallback("x", null, cause)).path("message").asText())
                    .isEqualTo("Product search is temporarily unavailable. Please try again in a moment.");

            assertThat(parse(toolExecutor.placeOrderFallback("{}", cause)).path("message").asText())
                    .isEqualTo("Unable to place your order right now. Please try again in a moment.");
        }

        @Test
        @DisplayName("the payment fallback reassures the shopper that the order survived")
        void thePaymentFallbackReassures() {
            assertThat(parse(toolExecutor.initiatePaymentFallback("{}", cause)).path("message").asText())
                    .contains("Your order has been saved");
        }

        @Test
        @DisplayName("no fallback leaks the underlying exception to the model")
        void noFallbackLeaksTheException() {
            assertThat(toolExecutor.getCartFallback(cause)).doesNotContain("connection refused");
            assertThat(toolExecutor.getOrderFallback(1001L, cause)).doesNotContain("IllegalStateException");
        }

        @Test
        @DisplayName("a fallback survives a cause with no message")
        void aFallbackSurvivesACauseWithNoMessage() {
            assertIsAnErrorPayload(toolExecutor.getCartFallback(new RuntimeException()), "getCart");
        }
    }

    @Nested
    @DisplayName("Resilience4j wiring")
    class Resilience4jWiring {

        private Method method(String name, Class<?>... parameterTypes) {
            try {
                return ToolExecutor.class.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ex) {
                throw new AssertionError(name + " is missing", ex);
            }
        }

        @Test
        @DisplayName("every tool is retried and circuit-broken under the shared instance")
        void everyToolIsGuarded() {
            Map<String, Class<?>[]> tools = Map.ofEntries(
                    Map.entry("searchProducts", new Class<?>[]{String.class, String.class}),
                    Map.entry("getProductDetails", new Class<?>[]{Long.class}),
                    Map.entry("getProductItemDetails", new Class<?>[]{Long.class, Long.class}),
                    Map.entry("getCart", new Class<?>[]{}),
                    Map.entry("addToCart", new Class<?>[]{String.class}),
                    Map.entry("clearCart", new Class<?>[]{}),
                    Map.entry("placeOrder", new Class<?>[]{String.class}),
                    Map.entry("buyNow", new Class<?>[]{String.class}),
                    Map.entry("getOrder", new Class<?>[]{Long.class}),
                    Map.entry("getMyOrders", new Class<?>[]{}),
                    Map.entry("getOrderItems", new Class<?>[]{Long.class}),
                    Map.entry("initiatePayment", new Class<?>[]{String.class}),
                    Map.entry("getAllAddresses", new Class<?>[]{}));

            tools.forEach((name, parameterTypes) -> {
                Method method = method(name, parameterTypes);

                assertThat(method.getAnnotation(Retry.class)).as("@Retry on %s", name).isNotNull();
                assertThat(method.getAnnotation(Retry.class).name()).isEqualTo(INSTANCE);

                CircuitBreaker breaker = method.getAnnotation(CircuitBreaker.class);
                assertThat(breaker).as("@CircuitBreaker on %s", name).isNotNull();
                assertThat(breaker.name()).isEqualTo(INSTANCE);
                assertThat(breaker.fallbackMethod()).isEqualTo(name + "Fallback");
            });
        }

        @Test
        @DisplayName("every declared fallback method actually exists with a matching signature")
        void everyFallbackExists() {
            List<Method> guarded = List.of(ToolExecutor.class.getMethods()).stream()
                    .filter(method -> method.getAnnotation(CircuitBreaker.class) != null)
                    .toList();

            assertThat(guarded).hasSize(13);

            guarded.forEach(method -> {
                String fallbackName = method.getAnnotation(CircuitBreaker.class).fallbackMethod();

                Class<?>[] parameterTypes = new Class<?>[method.getParameterCount() + 1];
                System.arraycopy(method.getParameterTypes(), 0, parameterTypes, 0, method.getParameterCount());
                parameterTypes[method.getParameterCount()] = Exception.class;

                // getOrderItemsFallback only accepts the throwable - resilience4j falls back
                // to the "throwable only" signature, which is legal.
                boolean exactSignature = hasMethod(fallbackName, parameterTypes);
                boolean throwableOnly = hasMethod(fallbackName, Exception.class);

                assertThat(exactSignature || throwableOnly)
                        .as("%s must exist for %s", fallbackName, method.getName())
                        .isTrue();
            });
        }

        private boolean hasMethod(String name, Class<?>... parameterTypes) {
            try {
                ToolExecutor.class.getMethod(name, parameterTypes);
                return true;
            } catch (NoSuchMethodException ex) {
                return false;
            }
        }

        @Test
        @DisplayName("every fallback returns raw JSON, never a typed object")
        void everyFallbackReturnsAString() {
            assertThat(ToolExecutor.class.getMethods())
                    .filteredOn(method -> method.getName().endsWith("Fallback"))
                    .isNotEmpty()
                    .allMatch(method -> method.getReturnType() == String.class);
        }
    }
}


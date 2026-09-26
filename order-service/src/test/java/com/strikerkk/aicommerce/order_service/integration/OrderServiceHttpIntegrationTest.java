package com.strikerkk.aicommerce.order_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.order_service.auth.UserContext;
import com.strikerkk.aicommerce.order_service.clients.CartClient;
import com.strikerkk.aicommerce.order_service.clients.ProductClient;
import com.strikerkk.aicommerce.order_service.clients.UserClient;
import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.repository.OrderItemRepository;
import com.strikerkk.aicommerce.order_service.repository.OrderRepository;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Order service HTTP wiring")
class OrderServiceHttpIntegrationTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_ROLE_HEADER = "X-user-role";
    private static final String USER_ID = "42";
    private static final String OTHER_USER_ID = "99";
    private static final Long ADDRESS_ID = TestDataFactory.ADDRESS_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private CartClient cartClient;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("product-service-call").reset();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private String placeBody(Long userId, Long addressId, Long productId, Long variantId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("userId", userId);
        body.put("addressId", addressId);
        body.put("productId", productId);
        body.put("variantId", variantId);
        return objectMapper.writeValueAsString(body);
    }

    private Order persistedOrder(Long userId, OrderStatus status) {
        Order order = Order.builder()
                .userId(userId)
                .addressId(ADDRESS_ID)
                .totalAmount(new BigDecimal("499.00"))
                .deliveryCharges(new BigDecimal("40.00"))
                .needToPay(new BigDecimal("539.00"))
                .status(status)
                .orderItems(new ArrayList<>())
                .build();

        order.getOrderItems().add(com.strikerkk.aicommerce.order_service.entity.OrderItem.builder()
                .order(order)
                .productId(1L)
                .variantId(2L)
                .quantity(1)
                .productName(TestDataFactory.PRODUCT_NAME)
                .productBrand(TestDataFactory.BRAND)
                .productImageUrl(TestDataFactory.IMAGE_URL)
                .size(TestDataFactory.SIZE)
                .color(TestDataFactory.COLOR)
                .priceAtOrder(new BigDecimal("499.00"))
                .lineTotal(new BigDecimal("499.00"))
                .build());

        return orderRepository.saveAndFlush(order);
    }

    // ------------------------------------------------------------------
    // placing an order
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("POST /orders")
    class PlaceOrder {

        @Test
        @DisplayName("stores the order with its lines and empties the cart")
        void shouldStoreTheOrderAndEmptyTheCart() throws Exception {
            when(cartClient.getCartItems()).thenReturn(List.of(
                    TestDataFactory.cartItemResponse(1L, 2L, 2, new BigDecimal("499.00"))));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());

            mockMvc.perform(post("/orders")
                            .header(USER_ID_HEADER, USER_ID)
                            .header(USER_ROLE_HEADER, "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(placeBody(42L, ADDRESS_ID, null, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.totalAmount").value(998.00))
                    .andExpect(jsonPath("$.data.deliveryCharges").value(40))
                    .andExpect(jsonPath("$.data.needToPay").value(1038.00))
                    .andExpect(jsonPath("$.data.address").value(TestDataFactory.FORMATTED_ADDRESS))
                    .andExpect(jsonPath("$.data.orderItems.length()").value(1));

            List<Order> stored = orderRepository.findAllByUserId(42L);
            assertThat(stored).hasSize(1);
            assertThat(stored.getFirst().getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(stored.getFirst().getNeedToPay()).isEqualByComparingTo("1038.00");
            // The lines live in their own table - read them there, the entity here is detached.
            assertThat(orderItemRepository.count()).isEqualTo(1);

            verify(cartClient).clearCart();
        }

        @Test
        @DisplayName("delivers a big order for free")
        void shouldDeliverABigOrderForFree() throws Exception {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());

            mockMvc.perform(post("/orders")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(placeBody(42L, ADDRESS_ID, null, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.deliveryCharges").value(0))
                    .andExpect(jsonPath("$.data.needToPay").value(8999.00));
        }

        @Test
        @DisplayName("refuses an empty cart and stores nothing")
        void shouldRefuseAnEmptyCart() throws Exception {
            when(cartClient.getCartItems()).thenReturn(List.of());
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());

            mockMvc.perform(post("/orders")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(placeBody(42L, ADDRESS_ID, null, null)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Cart is empty, cannot place order"));

            assertThat(orderRepository.count()).isZero();
        }

        @Test
        @DisplayName("refuses a call that carries no gateway header")
        void shouldRefuseAnAnonymousCall() throws Exception {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(placeBody(42L, ADDRESS_ID, null, null)))
                    .andExpect(status().isInternalServerError());

            assertThat(orderRepository.count()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // buy now
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("POST /orders/buy-now")
    class BuyNow {

        @Test
        @DisplayName("stores a single line order straight from product-service")
        void shouldStoreASingleLineOrder() throws Exception {
            when(productClient.getProductItemDetails(1L, 2L))
                    .thenReturn(TestDataFactory.productItemResponse(new BigDecimal("499.00")));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());

            mockMvc.perform(post("/orders/buy-now")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(placeBody(42L, ADDRESS_ID, 1L, 2L)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.orderItems.length()").value(1))
                    .andExpect(jsonPath("$.data.orderItems[0].quantity").value(1))
                    .andExpect(jsonPath("$.data.needToPay").value(539.00));

            assertThat(orderRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("never touches the cart")
        void shouldNeverTouchTheCart() throws Exception {
            when(productClient.getProductItemDetails(1L, 2L)).thenReturn(TestDataFactory.productItemResponse());
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());

            mockMvc.perform(post("/orders/buy-now")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(placeBody(42L, ADDRESS_ID, 1L, 2L)))
                    .andExpect(status().isCreated());

            org.mockito.Mockito.verifyNoInteractions(cartClient);
        }

        @Test
        @DisplayName("refuses a body claiming another user")
        void shouldRefuseASpoofedUser() throws Exception {
            mockMvc.perform(post("/orders/buy-now")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(placeBody(99L, ADDRESS_ID, 1L, 2L)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("User is not matching with auth userId"));

            assertThat(orderRepository.count()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("reading orders")
    class Reading {

        @Test
        @DisplayName("GET /orders/{id} returns the order of its owner")
        void shouldReturnTheOrderOfItsOwner() throws Exception {
            Order order = persistedOrder(42L, OrderStatus.CONFIRMED);

            mockMvc.perform(get("/orders/{orderId}", order.getId()).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(order.getId()))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data.orderItems.length()").value(1));
        }

        @Test
        @DisplayName("GET /orders/{id} hides the order from anybody else")
        void shouldHideTheOrderFromAnybodyElse() throws Exception {
            Order order = persistedOrder(42L, OrderStatus.PENDING);

            mockMvc.perform(get("/orders/{orderId}", order.getId()).header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Order is not found"));
        }

        @Test
        @DisplayName("GET /orders/my-orders summarises only my own orders")
        void shouldSummariseOnlyMyOrders() throws Exception {
            persistedOrder(42L, OrderStatus.PENDING);
            persistedOrder(99L, OrderStatus.PENDING);

            mockMvc.perform(get("/orders/my-orders").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].totalItems").value(1))
                    .andExpect(jsonPath("$.data[0].firstItemName").value(TestDataFactory.PRODUCT_NAME));
        }

        @Test
        @DisplayName("GET /orders/{id}/items returns every line")
        void shouldReturnEveryLine() throws Exception {
            Order order = persistedOrder(42L, OrderStatus.PENDING);

            mockMvc.perform(get("/orders/{orderId}/items", order.getId()).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].productName").value(TestDataFactory.PRODUCT_NAME))
                    .andExpect(jsonPath("$.data[0].lineTotal").value(499.00));
        }

        @Test
        @DisplayName("GET /orders/{id}/items refuses a foreign order")
        void shouldRefuseAForeignOrdersLines() throws Exception {
            Order order = persistedOrder(42L, OrderStatus.PENDING);

            mockMvc.perform(get("/orders/{orderId}/items", order.getId()).header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------
    // cancelling
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /orders/{id}/cancel")
    class Cancelling {

        @Test
        @DisplayName("cancels my pending order and stores the new status")
        void shouldCancelMyPendingOrder() throws Exception {
            Order order = persistedOrder(42L, OrderStatus.PENDING);

            mockMvc.perform(patch("/orders/{orderId}/cancel", order.getId()).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));

            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("refuses to cancel a delivered order")
        void shouldRefuseToCancelADeliveredOrder() throws Exception {
            Order order = persistedOrder(42L, OrderStatus.DELIVERED);

            mockMvc.perform(patch("/orders/{orderId}/cancel", order.getId()).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Delivered order cannot be cancelled"));

            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("refuses to cancel the order of somebody else")
        void shouldRefuseToCancelAForeignOrder() throws Exception {
            Order order = persistedOrder(42L, OrderStatus.PENDING);

            mockMvc.perform(patch("/orders/{orderId}/cancel", order.getId()).header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Order not found"));

            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("answers 404 for an order that never existed")
        void shouldAnswer404ForAnUnknownOrder() throws Exception {
            mockMvc.perform(patch("/orders/{orderId}/cancel", 404_404L).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------
    // header propagation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the gateway header decides who the order belongs to, never the body")
    void theHeaderDecidesTheOwner() throws Exception {
        when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
        when(userClient.getAddressByAddressId(anyLong())).thenReturn(TestDataFactory.addressResponse());

        mockMvc.perform(post("/orders")
                        .header(USER_ID_HEADER, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeBody(99L, ADDRESS_ID, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(42));

        assertThat(orderRepository.findAllByUserId(99L)).isEmpty();
        assertThat(orderRepository.findAllByUserId(42L)).hasSize(1);
    }
}



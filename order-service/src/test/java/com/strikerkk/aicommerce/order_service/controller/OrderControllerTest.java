package com.strikerkk.aicommerce.order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.order_service.dto.request.PlaceOrderRequest;
import com.strikerkk.aicommerce.order_service.dto.response.OrderItemResponse;
import com.strikerkk.aicommerce.order_service.dto.response.OrderResponse;
import com.strikerkk.aicommerce.order_service.dto.response.OrderSummaryResponse;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.order_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.order_service.service.OrderService;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController")
class OrderControllerTest {

    private static final Long ORDER_ID = TestDataFactory.ORDER_ID;

    /** "Anything but a success" - a malformed request never reaches the service. */
    private static final org.hamcrest.Matcher<Integer> FAILED =
            org.hamcrest.Matchers.greaterThanOrEqualTo(400);

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @Captor
    private ArgumentCaptor<PlaceOrderRequest> requestCaptor;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private OrderItemResponse itemResponse() {
        OrderItemResponse item = new OrderItemResponse();
        item.setId(TestDataFactory.ORDER_ITEM_ID);
        item.setProductId(TestDataFactory.PRODUCT_ID);
        item.setVariantId(TestDataFactory.VARIANT_ID);
        item.setProductName(TestDataFactory.PRODUCT_NAME);
        item.setProductBrand(TestDataFactory.BRAND);
        item.setProductImageUrl(TestDataFactory.IMAGE_URL);
        item.setSize(TestDataFactory.SIZE);
        item.setColor(TestDataFactory.COLOR);
        item.setQuantity(1);
        item.setPriceAtOrder(TestDataFactory.PRICE);
        item.setLineTotal(TestDataFactory.PRICE);
        return item;
    }

    private OrderResponse orderResponse(OrderStatus status) {
        OrderResponse response = new OrderResponse();
        response.setId(ORDER_ID);
        response.setUserId(TestDataFactory.USER_ID);
        response.setAddressId(TestDataFactory.ADDRESS_ID);
        response.setAddress(TestDataFactory.FORMATTED_ADDRESS);
        response.setTotalAmount(TestDataFactory.PRICE);
        response.setDeliveryCharges(BigDecimal.ZERO);
        response.setNeedToPay(TestDataFactory.PRICE);
        response.setStatus(status);
        response.setOrderItems(List.of(itemResponse()));
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());
        return response;
    }

    private OrderSummaryResponse summaryResponse() {
        OrderSummaryResponse summary = new OrderSummaryResponse();
        summary.setId(ORDER_ID);
        summary.setFirstItemName(TestDataFactory.PRODUCT_NAME);
        summary.setFirstItemImageUrl(TestDataFactory.IMAGE_URL);
        summary.setTotalItems(1);
        summary.setTotalAmount(TestDataFactory.PRICE);
        summary.setStatus(OrderStatus.PENDING);
        summary.setCreatedAt(LocalDateTime.now());
        return summary;
    }

    private String body(Long userId, Long addressId, Long productId, Long variantId) throws Exception {
        return objectMapper.writeValueAsString(
                TestDataFactory.placeOrderRequest(userId, addressId, productId, variantId));
    }

    // ------------------------------------------------------------------
    // POST /orders
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("POST /orders")
    class PlaceOrder {

        @Test
        @DisplayName("answers 201 with the pending order")
        void shouldAnswer201() throws Exception {
            when(orderService.placeOrder(any(PlaceOrderRequest.class)))
                    .thenReturn(orderResponse(OrderStatus.PENDING));

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.USER_ID, TestDataFactory.ADDRESS_ID, null, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("You order has been placed, Payment is pending"))
                    .andExpect(jsonPath("$.data.id").value(ORDER_ID))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.address").value(TestDataFactory.FORMATTED_ADDRESS))
                    .andExpect(jsonPath("$.data.orderItems.length()").value(1))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("hands the deserialized body over to the service")
        void shouldHandTheBodyOver() throws Exception {
            when(orderService.placeOrder(any(PlaceOrderRequest.class)))
                    .thenReturn(orderResponse(OrderStatus.PENDING));

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.USER_ID, 77L, 1L, 2L)))
                    .andExpect(status().isCreated());

            verify(orderService).placeOrder(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getUserId()).isEqualTo(TestDataFactory.USER_ID);
            assertThat(requestCaptor.getValue().getAddressId()).isEqualTo(77L);
            assertThat(requestCaptor.getValue().getProductId()).isEqualTo(1L);
            assertThat(requestCaptor.getValue().getVariantId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("turns an empty cart into a 500 carrying the reason")
        void shouldReportAnEmptyCart() throws Exception {
            when(orderService.placeOrder(any(PlaceOrderRequest.class)))
                    .thenThrow(new RuntimeException("Cart is empty, cannot place order"));

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.USER_ID, TestDataFactory.ADDRESS_ID, null, null)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Cart is empty, cannot place order"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("rejects a body that is not JSON before reaching the service")
        void shouldRejectANonJsonBody() throws Exception {
            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("not json"))
                    .andExpect(status().is(FAILED));

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("requires a body at all")
        void shouldRequireABody() throws Exception {
            mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().is(FAILED));

            verifyNoInteractions(orderService);
        }
    }

    // ------------------------------------------------------------------
    // POST /orders/buy-now
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("POST /orders/buy-now")
    class BuyNow {

        @Test
        @DisplayName("answers 201 with its own message")
        void shouldAnswer201() throws Exception {
            when(orderService.buyNow(any(PlaceOrderRequest.class)))
                    .thenReturn(orderResponse(OrderStatus.PENDING));

            mockMvc.perform(post("/orders/buy-now")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.USER_ID, TestDataFactory.ADDRESS_ID, 1L, 2L)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message")
                            .value("Your order via buy now has been placed, Payment is pending"))
                    .andExpect(jsonPath("$.data.needToPay").value(TestDataFactory.PRICE.doubleValue()));
        }

        @Test
        @DisplayName("never places a cart order by mistake")
        void shouldNotPlaceACartOrder() throws Exception {
            when(orderService.buyNow(any(PlaceOrderRequest.class)))
                    .thenReturn(orderResponse(OrderStatus.PENDING));

            mockMvc.perform(post("/orders/buy-now")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.USER_ID, TestDataFactory.ADDRESS_ID, 1L, 2L)))
                    .andExpect(status().isCreated());

            verify(orderService).buyNow(any(PlaceOrderRequest.class));
            verify(orderService, org.mockito.Mockito.never()).placeOrder(any(PlaceOrderRequest.class));
        }

        @Test
        @DisplayName("turns a spoofed user into a 500 carrying the reason")
        void shouldReportASpoofedUser() throws Exception {
            when(orderService.buyNow(any(PlaceOrderRequest.class)))
                    .thenThrow(new RuntimeException("User is not matching with auth userId"));

            mockMvc.perform(post("/orders/buy-now")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.OTHER_USER_ID, TestDataFactory.ADDRESS_ID, 1L, 2L)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("User is not matching with auth userId"));
        }

        @Test
        @DisplayName("turns an unreachable product-service into a 500")
        void shouldReportAnUnreachableProductService() throws Exception {
            when(orderService.buyNow(any(PlaceOrderRequest.class)))
                    .thenThrow(new RuntimeException("Product service unavailable"));

            mockMvc.perform(post("/orders/buy-now")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestDataFactory.USER_ID, TestDataFactory.ADDRESS_ID, 1L, 2L)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Product service unavailable"));
        }
    }

    // ------------------------------------------------------------------
    // GET /orders/{orderId}
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("GET /orders/{orderId}")
    class GetOrderById {

        @Test
        @DisplayName("answers 200 with the order")
        void shouldAnswer200() throws Exception {
            when(orderService.getOrderById(ORDER_ID)).thenReturn(orderResponse(OrderStatus.CONFIRMED));

            mockMvc.perform(get("/orders/{orderId}", ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Fetched order details successfully"))
                    .andExpect(jsonPath("$.data.id").value(ORDER_ID))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("passes the path variable through as a number")
        void shouldPassThePathVariable() throws Exception {
            when(orderService.getOrderById(9L)).thenReturn(orderResponse(OrderStatus.PENDING));

            mockMvc.perform(get("/orders/{orderId}", 9L)).andExpect(status().isOk());

            verify(orderService).getOrderById(9L);
        }

        @Test
        @DisplayName("answers 404 for an order of somebody else")
        void shouldAnswer404() throws Exception {
            when(orderService.getOrderById(ORDER_ID))
                    .thenThrow(new ResourceNotFoundException("Order is not found"));

            mockMvc.perform(get("/orders/{orderId}", ORDER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Order is not found"));
        }

        @Test
        @DisplayName("refuses an id that is not a number")
        void shouldRefuseANonNumericId() throws Exception {
            mockMvc.perform(get("/orders/abc")).andExpect(status().is(FAILED));

            verifyNoInteractions(orderService);
        }
    }

    // ------------------------------------------------------------------
    // GET /orders/my-orders
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("GET /orders/my-orders")
    class GetMyOrders {

        @Test
        @DisplayName("answers 200 with the summaries")
        void shouldAnswer200() throws Exception {
            when(orderService.getOrdersByUserId()).thenReturn(List.of(summaryResponse()));

            mockMvc.perform(get("/orders/my-orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Order summary response"))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(ORDER_ID))
                    .andExpect(jsonPath("$.data[0].totalItems").value(1))
                    .andExpect(jsonPath("$.data[0].firstItemName").value(TestDataFactory.PRODUCT_NAME))
                    .andExpect(jsonPath("$.data[0].firstItemImageUrl").value(TestDataFactory.IMAGE_URL));
        }

        @Test
        @DisplayName("answers 200 with an empty list when nothing was ever ordered")
        void shouldAnswerAnEmptyList() throws Exception {
            when(orderService.getOrdersByUserId()).thenReturn(List.of());

            mockMvc.perform(get("/orders/my-orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("wins over /orders/{orderId} - the literal mapping is the more specific one")
        void shouldWinOverThePathVariable() throws Exception {
            when(orderService.getOrdersByUserId()).thenReturn(List.of());

            mockMvc.perform(get("/orders/my-orders")).andExpect(status().isOk());

            verify(orderService).getOrdersByUserId();
            verify(orderService, org.mockito.Mockito.never()).getOrderById(any());
        }
    }

    // ------------------------------------------------------------------
    // PATCH /orders/{orderId}/cancel
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /orders/{orderId}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("answers 200 with the cancelled order")
        void shouldAnswer200() throws Exception {
            when(orderService.cancelOrder(ORDER_ID)).thenReturn(orderResponse(OrderStatus.CANCELLED));

            mockMvc.perform(patch("/orders/{orderId}/cancel", ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("You order has been cancelled"))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));

            verify(orderService).cancelOrder(ORDER_ID);
        }

        @Test
        @DisplayName("answers 404 for an unknown order")
        void shouldAnswer404() throws Exception {
            when(orderService.cancelOrder(ORDER_ID))
                    .thenThrow(new ResourceNotFoundException("Order not found"));

            mockMvc.perform(patch("/orders/{orderId}/cancel", ORDER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Order not found"));
        }

        @Test
        @DisplayName("answers 500 when the order was already delivered")
        void shouldReportADeliveredOrder() throws Exception {
            when(orderService.cancelOrder(ORDER_ID))
                    .thenThrow(new RuntimeException("Delivered order cannot be cancelled"));

            mockMvc.perform(patch("/orders/{orderId}/cancel", ORDER_ID))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Delivered order cannot be cancelled"));
        }

        @Test
        @DisplayName("is not reachable with GET - cancelling is a state change")
        void shouldNotBeReachableWithGet() throws Exception {
            mockMvc.perform(get("/orders/{orderId}/cancel", ORDER_ID))
                    .andExpect(status().is4xxClientError());

            verifyNoInteractions(orderService);
        }
    }

    // ------------------------------------------------------------------
    // GET /orders/{orderId}/items
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("GET /orders/{orderId}/items")
    class GetOrderItems {

        @Test
        @DisplayName("answers 200 with every line")
        void shouldAnswer200() throws Exception {
            when(orderService.getOrderItems(ORDER_ID)).thenReturn(List.of(itemResponse()));

            mockMvc.perform(get("/orders/{orderId}/items", ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Fetched order items successfully"))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].productName").value(TestDataFactory.PRODUCT_NAME))
                    .andExpect(jsonPath("$.data[0].size").value(TestDataFactory.SIZE))
                    .andExpect(jsonPath("$.data[0].color").value(TestDataFactory.COLOR))
                    .andExpect(jsonPath("$.data[0].quantity").value(1));
        }

        @Test
        @DisplayName("answers 200 with an empty list for an order without lines")
        void shouldAnswerAnEmptyList() throws Exception {
            when(orderService.getOrderItems(ORDER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/orders/{orderId}/items", ORDER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }

        @Test
        @DisplayName("answers 404 for an unknown order")
        void shouldAnswer404() throws Exception {
            when(orderService.getOrderItems(ORDER_ID))
                    .thenThrow(new ResourceNotFoundException("Order not found"));

            mockMvc.perform(get("/orders/{orderId}/items", ORDER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Order not found"));
        }
    }

    // ------------------------------------------------------------------
    // routing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("routing")
    class Routing {

        @Test
        @DisplayName("every route sits under /orders")
        void everyRouteSitsUnderOrders() {
            assertThat(OrderController.class
                    .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class)
                    .value())
                    .containsExactly("/orders");
        }

        @Test
        @DisplayName("is a REST controller, so every answer is serialized")
        void isARestController() {
            assertThat(OrderController.class
                    .getAnnotation(org.springframework.web.bind.annotation.RestController.class))
                    .isNotNull();
        }

        @Test
        @DisplayName("exposes exactly six endpoints")
        void exposesExactlySixEndpoints() {
            assertThat(OrderController.class.getDeclaredMethods()).hasSize(6);
        }

        @Test
        @DisplayName("an unknown route is not silently served")
        void anUnknownRouteIsNotServed() throws Exception {
            mockMvc.perform(get("/orders/{orderId}/unknown", ORDER_ID))
                    .andExpect(status().is4xxClientError());

            verifyNoInteractions(orderService);
        }
    }
}





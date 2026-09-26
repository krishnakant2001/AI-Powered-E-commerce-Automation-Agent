package com.strikerkk.aicommerce.order_service.service;

import com.strikerkk.aicommerce.order_service.auth.UserContext;
import com.strikerkk.aicommerce.order_service.clients.CartClient;
import com.strikerkk.aicommerce.order_service.clients.UserClient;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.AddressResponse;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.CartItemResponse;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.order_service.dto.request.PlaceOrderRequest;
import com.strikerkk.aicommerce.order_service.dto.response.OrderItemResponse;
import com.strikerkk.aicommerce.order_service.dto.response.OrderResponse;
import com.strikerkk.aicommerce.order_service.dto.response.OrderSummaryResponse;
import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.entity.OrderItem;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.order_service.repository.OrderRepository;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    private static final Long USER_ID = TestDataFactory.USER_ID;
    private static final Long ORDER_ID = TestDataFactory.ORDER_ID;
    private static final Long ADDRESS_ID = TestDataFactory.ADDRESS_ID;
    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartClient cartClient;

    @Mock
    private UserClient userClient;

    @Mock
    private OrderResilience4j orderResilience4j;

    /** The real mapper: the mapping itself is part of the contract of the service. */
    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private OrderService orderService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @BeforeEach
    void signIn() {
        UserContext.setUserId(String.valueOf(USER_ID));
        UserContext.setUserRole("USER");
    }

    @AfterEach
    void signOut() {
        UserContext.clear();
    }

    /** Gives the saved order an id, exactly like the database would. */
    private void echoSavedOrder() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(ORDER_ID);
            }
            return order;
        });
    }

    // ------------------------------------------------------------------
    // placeOrder
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("placing an order from the cart")
    class PlaceOrder {

        private final PlaceOrderRequest request = TestDataFactory.placeOrderRequest();

        @Test
        @DisplayName("turns every cart line into an order line, snapshotting the product")
        void shouldSnapshotEveryCartLine() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.placeOrder(request);

            verify(orderRepository).save(orderCaptor.capture());
            OrderItem line = orderCaptor.getValue().getOrderItems().getFirst();

            assertThat(line.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(line.getVariantId()).isEqualTo(VARIANT_ID);
            assertThat(line.getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(line.getProductBrand()).isEqualTo(TestDataFactory.BRAND);
            assertThat(line.getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
            assertThat(line.getSize()).isEqualTo(TestDataFactory.SIZE);
            assertThat(line.getColor()).isEqualTo(TestDataFactory.COLOR);
            assertThat(line.getQuantity()).isEqualTo(1);
            assertThat(line.getPriceAtOrder()).isEqualByComparingTo(TestDataFactory.PRICE);
        }

        @Test
        @DisplayName("multiplies the price at add by the quantity to get the line total")
        void shouldComputeTheLineTotal() {
            CartItemResponse threeUnits =
                    TestDataFactory.cartItemResponse(PRODUCT_ID, VARIANT_ID, 3, new BigDecimal("100.00"));
            when(cartClient.getCartItems()).thenReturn(List.of(threeUnits));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.placeOrder(request);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getOrderItems().getFirst().getLineTotal())
                    .isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("adds every line total up into the order total")
        void shouldSumEveryLine() {
            when(cartClient.getCartItems()).thenReturn(List.of(
                    TestDataFactory.cartItemResponse(1L, 10L, 2, new BigDecimal("100.00")),
                    TestDataFactory.cartItemResponse(2L, 20L, 1, new BigDecimal("250.50"))));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.placeOrder(request);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo(new BigDecimal("450.50"));
            assertThat(orderCaptor.getValue().getOrderItems()).hasSize(2);
        }

        @Test
        @DisplayName("charges 40 for the delivery when the total stays below the 1999 threshold")
        void shouldChargeTheDeliveryBelowTheThreshold() {
            when(cartClient.getCartItems()).thenReturn(List.of(
                    TestDataFactory.cartItemResponse(PRODUCT_ID, VARIANT_ID, 1, TestDataFactory.CHEAP_PRICE)));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.placeOrder(request);

            assertThat(response.getTotalAmount()).isEqualByComparingTo(TestDataFactory.CHEAP_PRICE);
            assertThat(response.getDeliveryCharges()).isEqualByComparingTo(TestDataFactory.PAID_DELIVERY);
            assertThat(response.getNeedToPay()).isEqualByComparingTo(new BigDecimal("539.00"));
        }

        @Test
        @DisplayName("delivers for free once the total is strictly above 1999")
        void shouldDeliverForFreeAboveTheThreshold() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.placeOrder(request);

            assertThat(response.getDeliveryCharges()).isEqualByComparingTo(TestDataFactory.FREE_DELIVERY);
            assertThat(response.getNeedToPay()).isEqualByComparingTo(TestDataFactory.PRICE);
        }

        @Test
        @DisplayName("still charges the delivery for a total of exactly 1999 - the bound is exclusive")
        void shouldChargeTheDeliveryOnTheBoundary() {
            when(cartClient.getCartItems()).thenReturn(List.of(
                    TestDataFactory.cartItemResponse(PRODUCT_ID, VARIANT_ID, 1, TestDataFactory.THRESHOLD_PRICE)));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.placeOrder(request);

            assertThat(response.getDeliveryCharges()).isEqualByComparingTo(TestDataFactory.PAID_DELIVERY);
            assertThat(response.getNeedToPay()).isEqualByComparingTo(new BigDecimal("2039"));
        }

        @Test
        @DisplayName("opens the order as PENDING for the signed in user and the requested address")
        void shouldOpenThePendingOrder() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.placeOrder(request);

            verify(orderRepository).save(orderCaptor.capture());
            Order saved = orderCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getAddressId()).isEqualTo(ADDRESS_ID);
        }

        @Test
        @DisplayName("takes the owner from the gateway header, never from the request body")
        void shouldTrustTheHeaderOnly() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            PlaceOrderRequest spoofed =
                    TestDataFactory.placeOrderRequest(TestDataFactory.OTHER_USER_ID, ADDRESS_ID, PRODUCT_ID, VARIANT_ID);

            orderService.placeOrder(spoofed);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("wires both sides of the order / line association before saving")
        void shouldWireBothSidesOfTheAssociation() {
            when(cartClient.getCartItems()).thenReturn(List.of(
                    TestDataFactory.cartItemResponse(1L, 10L, 1, TestDataFactory.PRICE),
                    TestDataFactory.cartItemResponse(2L, 20L, 1, TestDataFactory.PRICE)));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.placeOrder(request);

            verify(orderRepository).save(orderCaptor.capture());
            Order saved = orderCaptor.getValue();
            assertThat(saved.getOrderItems())
                    .hasSize(2)
                    .allSatisfy(item -> assertThat(item.getOrder()).isSameAs(saved));
        }

        @Test
        @DisplayName("empties the cart only after the order is safely saved")
        void shouldClearTheCartAfterSaving() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.placeOrder(request);

            InOrder ordered = inOrder(orderRepository, cartClient);
            ordered.verify(orderRepository).save(any(Order.class));
            ordered.verify(cartClient).clearCart();
        }

        @Test
        @DisplayName("returns the flattened delivery address of the request")
        void shouldReturnTheFlattenedAddress() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.placeOrder(request);

            assertThat(response.getAddress()).isEqualTo(TestDataFactory.FORMATTED_ADDRESS);
        }

        @Test
        @DisplayName("maps the saved order, with its lines, onto the response")
        void shouldMapTheSavedOrder() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.placeOrder(request);

            assertThat(response.getId()).isEqualTo(ORDER_ID);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getAddressId()).isEqualTo(ADDRESS_ID);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getOrderItems()).hasSize(1);

            OrderItemResponse line = response.getOrderItems().getFirst();
            assertThat(line.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(line.getVariantId()).isEqualTo(VARIANT_ID);
            assertThat(line.getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(line.getQuantity()).isEqualTo(1);
            assertThat(line.getLineTotal()).isEqualByComparingTo(TestDataFactory.PRICE);
        }

        @Test
        @DisplayName("refuses an empty cart and saves nothing")
        void shouldRefuseAnEmptyCart() {
            when(cartClient.getCartItems()).thenReturn(List.of());
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());

            assertThatThrownBy(() -> orderService.placeOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Cart is empty, cannot place order");

            verify(orderRepository, never()).save(any(Order.class));
            verify(cartClient, never()).clearCart();
        }

        @Test
        @DisplayName("refuses a null answer from cart-service the same way")
        void shouldRefuseANullCart() {
            when(cartClient.getCartItems()).thenReturn(null);
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());

            assertThatThrownBy(() -> orderService.placeOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Cart is empty, cannot place order");

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("bubbles a cart-service failure up instead of saving half an order")
        void shouldBubbleUpACartFailure() {
            when(cartClient.getCartItems()).thenThrow(new RuntimeException("cart-service down"));

            assertThatThrownBy(() -> orderService.placeOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("cart-service down");

            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("bubbles a user-service failure up instead of saving half an order")
        void shouldBubbleUpAnAddressFailure() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID))
                    .thenThrow(new RuntimeException("user-service down"));

            assertThatThrownBy(() -> orderService.placeOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("user-service down");

            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("without a caller id nothing is even attempted")
        void shouldRefuseAnAnonymousCaller() {
            UserContext.clear();

            assertThatThrownBy(() -> orderService.placeOrder(request))
                    .isInstanceOf(NumberFormatException.class);

            verifyNoInteractions(cartClient, userClient, orderRepository);
        }

        @Test
        @DisplayName("runs inside a transaction, so the order and its lines are all or nothing")
        void shouldBeTransactional() throws Exception {
            assertThat(OrderService.class
                    .getMethod("placeOrder", PlaceOrderRequest.class)
                    .getAnnotation(Transactional.class))
                    .isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // buyNow
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("buying a single item right away")
    class BuyNow {

        private final PlaceOrderRequest request = TestDataFactory.placeOrderRequest();

        @Test
        @DisplayName("builds a single line of quantity 1 out of the product payload")
        void shouldBuildASingleLine() {
            when(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productItemResponse());
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.buyNow(request);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getOrderItems()).hasSize(1);

            OrderItem line = orderCaptor.getValue().getOrderItems().getFirst();
            assertThat(line.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(line.getVariantId()).isEqualTo(VARIANT_ID);
            assertThat(line.getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(line.getProductBrand()).isEqualTo(TestDataFactory.BRAND);
            assertThat(line.getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
            assertThat(line.getSize()).isEqualTo(TestDataFactory.SIZE);
            assertThat(line.getColor()).isEqualTo(TestDataFactory.COLOR);
            assertThat(line.getQuantity()).isEqualTo(1);
            assertThat(line.getPriceAtOrder()).isEqualByComparingTo(TestDataFactory.PRICE);
            assertThat(line.getLineTotal()).isEqualByComparingTo(TestDataFactory.PRICE);
            assertThat(line.getOrder()).isSameAs(orderCaptor.getValue());
        }

        @Test
        @DisplayName("takes the variant id product-service answered with, not the one asked for")
        void shouldTrustTheVariantOfTheProductService() {
            ProductItemResponse item = TestDataFactory.productItemResponse();
            item.setVariantId(777L);
            when(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID)).thenReturn(item);
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.buyNow(request);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getOrderItems().getFirst().getVariantId()).isEqualTo(777L);
        }

        @Test
        @DisplayName("charges 40 for the delivery of a cheap item")
        void shouldChargeTheDeliveryOfACheapItem() {
            when(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productItemResponse(TestDataFactory.CHEAP_PRICE));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.buyNow(request);

            assertThat(response.getTotalAmount()).isEqualByComparingTo(TestDataFactory.CHEAP_PRICE);
            assertThat(response.getDeliveryCharges()).isEqualByComparingTo(TestDataFactory.PAID_DELIVERY);
            assertThat(response.getNeedToPay()).isEqualByComparingTo(new BigDecimal("539.00"));
        }

        @Test
        @DisplayName("delivers an expensive item for free")
        void shouldDeliverAnExpensiveItemForFree() {
            when(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productItemResponse());
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.buyNow(request);

            assertThat(response.getDeliveryCharges()).isEqualByComparingTo(TestDataFactory.FREE_DELIVERY);
            assertThat(response.getNeedToPay()).isEqualByComparingTo(TestDataFactory.PRICE);
        }

        @Test
        @DisplayName("opens the order as PENDING and flattens the address")
        void shouldOpenThePendingOrder() {
            when(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productItemResponse());
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.buyNow(request);

            assertThat(response.getId()).isEqualTo(ORDER_ID);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getAddressId()).isEqualTo(ADDRESS_ID);
            assertThat(response.getAddress()).isEqualTo(TestDataFactory.FORMATTED_ADDRESS);
            assertThat(response.getOrderItems()).hasSize(1);
        }

        @Test
        @DisplayName("never touches the cart - buy now is a side channel")
        void shouldNeverTouchTheCart() {
            when(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenReturn(TestDataFactory.productItemResponse());
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            orderService.buyNow(request);

            verifyNoInteractions(cartClient);
        }

        @Test
        @DisplayName("rejects a body that claims to be somebody else")
        void shouldRejectASpoofedUser() {
            PlaceOrderRequest spoofed = TestDataFactory.placeOrderRequest(
                    TestDataFactory.OTHER_USER_ID, ADDRESS_ID, PRODUCT_ID, VARIANT_ID);

            assertThatThrownBy(() -> orderService.buyNow(spoofed))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User is not matching with auth userId");

            verifyNoInteractions(orderResilience4j, userClient, orderRepository);
        }

        @Test
        @DisplayName("rejects a body without any user id")
        void shouldRejectAMissingUserId() {
            PlaceOrderRequest anonymous =
                    TestDataFactory.placeOrderRequest(null, ADDRESS_ID, PRODUCT_ID, VARIANT_ID);

            assertThatThrownBy(() -> orderService.buyNow(anonymous))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User is not matching with auth userId");

            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("lets the product-service fallback failure travel up, nothing is saved")
        void shouldBubbleUpTheProductFallback() {
            when(orderResilience4j.getItemDetails(PRODUCT_ID, VARIANT_ID))
                    .thenThrow(new RuntimeException("Product service unavailable"));

            assertThatThrownBy(() -> orderService.buyNow(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Product service unavailable");

            verifyNoInteractions(orderRepository);
        }

        @Test
        @DisplayName("without a caller id nothing is even attempted")
        void shouldRefuseAnAnonymousCaller() {
            UserContext.clear();

            assertThatThrownBy(() -> orderService.buyNow(request))
                    .isInstanceOf(NumberFormatException.class);

            verifyNoInteractions(orderResilience4j, userClient, orderRepository);
        }
    }

    // ------------------------------------------------------------------
    // getOrderById
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("reading one order")
    class GetOrderById {

        @Test
        @DisplayName("maps the order of the signed in user")
        void shouldMapTheOrder() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order()));

            OrderResponse response = orderService.getOrderById(ORDER_ID);

            assertThat(response.getId()).isEqualTo(ORDER_ID);
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getNeedToPay()).isEqualByComparingTo(TestDataFactory.PRICE);
            assertThat(response.getOrderItems()).hasSize(1);
        }

        @Test
        @DisplayName("scopes the lookup by the caller, so nobody reads somebody else's order")
        void shouldScopeTheLookupByTheCaller() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order()));

            orderService.getOrderById(ORDER_ID);

            verify(orderRepository).findByIdAndUserId(ORDER_ID, USER_ID);
        }

        @Test
        @DisplayName("reports a missing - or foreign - order as not found")
        void shouldReportAMissingOrder() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Order is not found");
        }

        @Test
        @DisplayName("falls back to the raw address id - only a freshly placed order carries the flat address")
        void shouldFallBackToTheRawAddressId() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order()));

            OrderResponse response = orderService.getOrderById(ORDER_ID);

            assertThat(response.getAddressId()).isEqualTo(ADDRESS_ID);
            // The mapper copies addressId into address; nothing calls user-service on a read.
            assertThat(response.getAddress()).isEqualTo(String.valueOf(ADDRESS_ID));
        }
    }

    // ------------------------------------------------------------------
    // getOrdersByUserId
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("listing my orders")
    class GetOrdersByUserId {

        @Test
        @DisplayName("summarises every order with its line count and first thumbnail")
        void shouldSummariseEveryOrder() {
            Order order = TestDataFactory.order();
            TestDataFactory.attachItem(order, 501L, 3L, 4L, 2, TestDataFactory.CHEAP_PRICE);
            when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of(order));

            List<OrderSummaryResponse> summaries = orderService.getOrdersByUserId();

            assertThat(summaries).hasSize(1);
            OrderSummaryResponse summary = summaries.getFirst();
            assertThat(summary.getId()).isEqualTo(ORDER_ID);
            assertThat(summary.getTotalItems()).isEqualTo(2);
            assertThat(summary.getFirstItemName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(summary.getFirstItemImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
            assertThat(summary.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(summary.getTotalAmount()).isEqualByComparingTo(TestDataFactory.PRICE);
        }

        @Test
        @DisplayName("counts the lines, not the units")
        void shouldCountTheLinesNotTheUnits() {
            Order order = TestDataFactory.emptyOrder(ORDER_ID, OrderStatus.PENDING);
            TestDataFactory.attachItem(order, 501L, 1L, 1L, 5, TestDataFactory.CHEAP_PRICE);

            when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of(order));

            assertThat(orderService.getOrdersByUserId().getFirst().getTotalItems()).isEqualTo(1);
        }

        @Test
        @DisplayName("keeps a line-less order in the list, simply without a thumbnail")
        void shouldKeepALineLessOrder() {
            when(orderRepository.findAllByUserId(USER_ID))
                    .thenReturn(List.of(TestDataFactory.emptyOrder(ORDER_ID, OrderStatus.CANCELLED)));

            OrderSummaryResponse summary = orderService.getOrdersByUserId().getFirst();

            assertThat(summary.getTotalItems()).isZero();
            assertThat(summary.getFirstItemName()).isNull();
            assertThat(summary.getFirstItemImageUrl()).isNull();
            assertThat(summary.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("returns an empty list for a user who never ordered")
        void shouldReturnAnEmptyList() {
            when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

            assertThat(orderService.getOrdersByUserId()).isEmpty();
        }

        @Test
        @DisplayName("keeps the order the repository returned")
        void shouldKeepTheRepositoryOrder() {
            when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
                    TestDataFactory.order(3L, USER_ID, OrderStatus.DELIVERED),
                    TestDataFactory.order(1L, USER_ID, OrderStatus.PENDING),
                    TestDataFactory.order(2L, USER_ID, OrderStatus.SHIPPED)));

            assertThat(orderService.getOrdersByUserId())
                    .extracting(OrderSummaryResponse::getId)
                    .containsExactly(3L, 1L, 2L);
        }

        @Test
        @DisplayName("only ever asks for the orders of the signed in user")
        void shouldOnlyAskForMyOrders() {
            when(orderRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

            orderService.getOrdersByUserId();

            verify(orderRepository).findAllByUserId(USER_ID);
        }
    }

    // ------------------------------------------------------------------
    // cancelOrder
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("cancelling an order")
    class CancelOrder {

        @Test
        @DisplayName("moves a pending order to CANCELLED and saves it")
        void shouldCancelAPendingOrder() {
            Order order = TestDataFactory.order();
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));
            echoSavedOrder();

            OrderResponse response = orderService.cancelOrder(ORDER_ID);

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("cancels a confirmed order too - the refund is somebody else's job")
        void shouldCancelAConfirmedOrder() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order(ORDER_ID, USER_ID, OrderStatus.CONFIRMED)));
            echoSavedOrder();

            assertThat(orderService.cancelOrder(ORDER_ID).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("refuses to cancel a delivered order")
        void shouldRefuseADeliveredOrder() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order(ORDER_ID, USER_ID, OrderStatus.DELIVERED)));

            assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Delivered order cannot be cancelled");

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("cancelling twice is harmless")
        void shouldTolerateADoubleCancel() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order(ORDER_ID, USER_ID, OrderStatus.CANCELLED)));
            echoSavedOrder();

            assertThat(orderService.cancelOrder(ORDER_ID).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("reports a missing - or foreign - order as not found")
        void shouldReportAMissingOrder() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Order not found");

            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("keeps the lines of the cancelled order for the history")
        void shouldKeepTheLines() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order()));
            echoSavedOrder();

            assertThat(orderService.cancelOrder(ORDER_ID).getOrderItems()).hasSize(1);
        }

        @Test
        @DisplayName("runs inside a transaction")
        void shouldBeTransactional() throws Exception {
            assertThat(OrderService.class
                    .getMethod("cancelOrder", Long.class)
                    .getAnnotation(Transactional.class))
                    .isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // getOrderItems
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("reading the lines of an order")
    class GetOrderItems {

        @Test
        @DisplayName("maps every line with its product snapshot")
        void shouldMapEveryLine() {
            Order order = TestDataFactory.order();
            TestDataFactory.attachItem(order, 501L, 9L, 8L, 3, TestDataFactory.CHEAP_PRICE);
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(order));

            List<OrderItemResponse> items = orderService.getOrderItems(ORDER_ID);

            assertThat(items).hasSize(2);
            assertThat(items.getFirst().getId()).isEqualTo(TestDataFactory.ORDER_ITEM_ID);
            assertThat(items.getFirst().getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(items.getFirst().getProductBrand()).isEqualTo(TestDataFactory.BRAND);
            assertThat(items.getFirst().getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
            assertThat(items.getFirst().getSize()).isEqualTo(TestDataFactory.SIZE);
            assertThat(items.getFirst().getColor()).isEqualTo(TestDataFactory.COLOR);

            assertThat(items.getLast().getProductId()).isEqualTo(9L);
            assertThat(items.getLast().getVariantId()).isEqualTo(8L);
            assertThat(items.getLast().getQuantity()).isEqualTo(3);
            assertThat(items.getLast().getPriceAtOrder()).isEqualByComparingTo(TestDataFactory.CHEAP_PRICE);
            assertThat(items.getLast().getLineTotal()).isEqualByComparingTo(new BigDecimal("1497.00"));
        }

        @Test
        @DisplayName("returns an empty list for an order without lines")
        void shouldReturnAnEmptyList() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.emptyOrder(ORDER_ID, OrderStatus.PENDING)));

            assertThat(orderService.getOrderItems(ORDER_ID)).isEmpty();
        }

        @Test
        @DisplayName("reports a missing - or foreign - order as not found")
        void shouldReportAMissingOrder() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderItems(ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Order not found");
        }

        @Test
        @DisplayName("scopes the lookup by the caller")
        void shouldScopeTheLookupByTheCaller() {
            when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order()));

            orderService.getOrderItems(ORDER_ID);

            verify(orderRepository).findByIdAndUserId(ORDER_ID, USER_ID);
        }
    }

    // ------------------------------------------------------------------
    // mapping guards
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("mapping guards")
    class Mapping {

        @Test
        @DisplayName("the default mapper copies the address id into the flat address, so it must be overwritten")
        void theAddressIdIsCopiedIntoTheAddress() {
            OrderResponse response = new ModelMapper().map(TestDataFactory.order(), OrderResponse.class);

            assertThat(response.getAddressId()).isEqualTo(ADDRESS_ID);
            assertThat(response.getAddress()).isEqualTo(String.valueOf(ADDRESS_ID));
        }

        @Test
        @DisplayName("placing an order therefore overwrites it with the real, flattened address")
        void placingAnOrderOverwritesTheAddress() {
            when(cartClient.getCartItems()).thenReturn(List.of(TestDataFactory.cartItemResponse()));
            when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(TestDataFactory.addressResponse());
            echoSavedOrder();

            OrderResponse response = orderService.placeOrder(TestDataFactory.placeOrderRequest());

            assertThat(response.getAddress())
                    .isEqualTo(TestDataFactory.FORMATTED_ADDRESS)
                    .isNotEqualTo(String.valueOf(ADDRESS_ID));
        }

        @Test
        @DisplayName("a line keeps its own id, never the id of its order")
        void aLineKeepsItsOwnId() {
            Order order = TestDataFactory.order();
            OrderItem line = order.getOrderItems().getFirst();

            OrderItemResponse response = new ModelMapper().map(line, OrderItemResponse.class);

            assertThat(response.getId()).isEqualTo(TestDataFactory.ORDER_ITEM_ID);
        }

        @Test
        @DisplayName("an order with an empty line list maps to an empty response list")
        void anEmptyOrderMapsToAnEmptyList() {
            Order order = Order.builder()
                    .id(1L)
                    .userId(USER_ID)
                    .addressId(ADDRESS_ID)
                    .totalAmount(BigDecimal.ZERO)
                    .deliveryCharges(BigDecimal.ZERO)
                    .needToPay(BigDecimal.ZERO)
                    .status(OrderStatus.PENDING)
                    .orderItems(new ArrayList<>())
                    .build();

            OrderResponse response = new ModelMapper().map(order, OrderResponse.class);

            assertThat(response.getOrderItems()).isEmpty();
        }
    }

    @Test
    @DisplayName("reads the address of the request even when the cart turns out to be empty")
    void readsTheAddressBeforeCheckingTheCart() {
        AddressResponse address = TestDataFactory.addressResponse();
        when(cartClient.getCartItems()).thenReturn(List.of());
        when(userClient.getAddressByAddressId(ADDRESS_ID)).thenReturn(address);

        assertThatThrownBy(() -> orderService.placeOrder(TestDataFactory.placeOrderRequest()))
                .isInstanceOf(RuntimeException.class);

        verify(userClient).getAddressByAddressId(ADDRESS_ID);
    }
}




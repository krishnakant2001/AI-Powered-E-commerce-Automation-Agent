package com.strikerkk.aicommerce.order_service.integration;

import com.strikerkk.aicommerce.order_service.consumers.PaymentSuccessConsumer;
import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.entity.OrderItem;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.event.OrderConfirmedEvent;
import com.strikerkk.aicommerce.order_service.repository.OrderItemRepository;
import com.strikerkk.aicommerce.order_service.repository.OrderRepository;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("The payment-success listener against the database")
class PaymentSuccessConsumerIntegrationTest {

    private static final String CONFIRMED_TOPIC = "order-confirmed-topic";

    @Autowired
    private PaymentSuccessConsumer consumer;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @SuppressWarnings("rawtypes")
    @MockitoBean
    private KafkaTemplate kafkaTemplate;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private Order persistedOrder(OrderStatus status) {
        Order order = Order.builder()
                .userId(TestDataFactory.USER_ID)
                .addressId(TestDataFactory.ADDRESS_ID)
                .totalAmount(new BigDecimal("499.00"))
                .deliveryCharges(new BigDecimal("40.00"))
                .needToPay(new BigDecimal("539.00"))
                .status(status)
                .orderItems(new ArrayList<>())
                .build();

        order.getOrderItems().add(OrderItem.builder()
                .order(order)
                .productId(1L)
                .variantId(2L)
                .quantity(3)
                .productName(TestDataFactory.PRODUCT_NAME)
                .productBrand(TestDataFactory.BRAND)
                .productImageUrl(TestDataFactory.IMAGE_URL)
                .size(TestDataFactory.SIZE)
                .color(TestDataFactory.COLOR)
                .priceAtOrder(new BigDecimal("499.00"))
                .lineTotal(new BigDecimal("1497.00"))
                .build());

        return orderRepository.saveAndFlush(order);
    }

    @Test
    @DisplayName("confirms the stored order and announces it with its lines")
    void shouldConfirmTheStoredOrder() {
        Order order = persistedOrder(OrderStatus.PENDING);

        consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(order.getId(), "SUCCESS"));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);

        ArgumentCaptor<OrderConfirmedEvent> captor = ArgumentCaptor.forClass(OrderConfirmedEvent.class);
        verify(kafkaTemplate).send(eq(CONFIRMED_TOPIC), captor.capture());

        assertThat(captor.getValue().getOrderId()).isEqualTo(order.getId());
        assertThat(captor.getValue().getOrderPlacedItems()).hasSize(1);
        assertThat(captor.getValue().getOrderPlacedItems().getFirst().getProductId()).isEqualTo(1L);
        assertThat(captor.getValue().getOrderPlacedItems().getFirst().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("leaves a stored order alone when the payment failed")
    void shouldLeaveTheOrderAloneOnAFailedPayment() {
        Order order = persistedOrder(OrderStatus.PENDING);

        consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(order.getId(), "FAILED"));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        verify(kafkaTemplate, never()).send(eq(CONFIRMED_TOPIC), any(OrderConfirmedEvent.class));
    }

    @Test
    @DisplayName("ignores a payment for an order that is not in the database")
    void shouldIgnoreAnUnknownOrder() {
        consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(404_404L, "SUCCESS"));

        verify(kafkaTemplate, never()).send(eq(CONFIRMED_TOPIC), any(OrderConfirmedEvent.class));
    }

    @Test
    @DisplayName("never confirms an already cancelled order")
    void shouldNeverConfirmACancelledOrder() {
        Order order = persistedOrder(OrderStatus.CANCELLED);

        consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(order.getId(), "SUCCESS"));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        verify(kafkaTemplate, never()).send(eq(CONFIRMED_TOPIC), any(OrderConfirmedEvent.class));
    }

    @Test
    @DisplayName("the listener container for payment-success-topic is registered")
    void theListenerContainerIsRegistered() {
        assertThat(listenerRegistry.getListenerContainers())
                .anySatisfy(container ->
                        assertThat(container.getContainerProperties().getTopics())
                                .contains("payment-success-topic"));
    }
}



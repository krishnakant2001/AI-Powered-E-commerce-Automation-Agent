package com.strikerkk.aicommerce.order_service.consumers;

import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.event.OrderConfirmedEvent;
import com.strikerkk.aicommerce.order_service.event.OrderPlacedItem;
import com.strikerkk.aicommerce.order_service.repository.OrderRepository;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import com.strikerkk.aicommerce.payment_service.event.PaymentSuccessEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentSuccessConsumer")
class PaymentSuccessConsumerTest {

    private static final Long ORDER_ID = TestDataFactory.ORDER_ID;
    private static final String CONFIRMED_TOPIC = "order-confirmed-topic";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<Long, OrderConfirmedEvent> orderConfirmedEventKafkaTemplate;

    @InjectMocks
    private PaymentSuccessConsumer consumer;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Captor
    private ArgumentCaptor<OrderConfirmedEvent> eventCaptor;

    // ------------------------------------------------------------------
    // the happy path
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("when the payment succeeded")
    class PaymentSucceeded {

        @Test
        @DisplayName("confirms the pending order")
        void shouldConfirmThePendingOrder() {
            Order order = TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.PENDING);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "SUCCESS"));

            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("announces the confirmation on the order-confirmed topic")
        void shouldAnnounceTheConfirmation() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.PENDING)));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "SUCCESS"));

            verify(orderConfirmedEventKafkaTemplate).send(eq(CONFIRMED_TOPIC), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("carries every line of the order, so the stock can be released")
        void shouldCarryEveryLine() {
            Order order = TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.PENDING);
            TestDataFactory.attachItem(order, 501L, 11L, 22L, 4, TestDataFactory.CHEAP_PRICE);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "SUCCESS"));

            verify(orderConfirmedEventKafkaTemplate).send(eq(CONFIRMED_TOPIC), eventCaptor.capture());

            assertThat(eventCaptor.getValue().getOrderPlacedItems())
                    .hasSize(2)
                    .extracting(OrderPlacedItem::getId,
                            OrderPlacedItem::getProductId,
                            OrderPlacedItem::getVariantId,
                            OrderPlacedItem::getQuantity)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(
                                    TestDataFactory.ORDER_ITEM_ID,
                                    TestDataFactory.PRODUCT_ID,
                                    TestDataFactory.VARIANT_ID,
                                    1),
                            org.assertj.core.groups.Tuple.tuple(501L, 11L, 22L, 4));
        }

        @Test
        @DisplayName("publishes an empty line list for an order without lines")
        void shouldPublishAnEmptyLineList() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.emptyOrder(ORDER_ID, OrderStatus.PENDING)));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "SUCCESS"));

            verify(orderConfirmedEventKafkaTemplate).send(eq(CONFIRMED_TOPIC), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getOrderPlacedItems()).isEmpty();
        }

        @Test
        @DisplayName("looks the order up by the id carried by the payment event")
        void shouldLookTheOrderUpByTheEventId() {
            when(orderRepository.findById(555L))
                    .thenReturn(Optional.of(TestDataFactory.order(555L, TestDataFactory.USER_ID, OrderStatus.PENDING)));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(555L, "SUCCESS"));

            verify(orderRepository).findById(555L);
        }
    }

    // ------------------------------------------------------------------
    // everything that must be ignored
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("when the event must be ignored")
    class Ignored {

        @Test
        @DisplayName("an unknown order is logged and dropped, never retried into a crash")
        void shouldDropAnUnknownOrder() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "SUCCESS"));

            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(orderConfirmedEventKafkaTemplate);
        }

        @Test
        @DisplayName("an event without an order id is dropped as well")
        void shouldDropAnEventWithoutAnOrderId() {
            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(null, "SUCCESS"));

            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(orderConfirmedEventKafkaTemplate);
        }

        @Test
        @DisplayName("a failed payment leaves the order pending")
        void shouldIgnoreAFailedPayment() {
            Order order = TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.PENDING);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "FAILED"));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(orderConfirmedEventKafkaTemplate);
        }

        @Test
        @DisplayName("a missing payment status is not mistaken for a success")
        void shouldIgnoreAMissingStatus() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.PENDING)));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, null));

            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(orderConfirmedEventKafkaTemplate);
        }

        @Test
        @DisplayName("the status is matched case sensitively")
        void shouldMatchTheStatusCaseSensitively() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.PENDING)));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "success"));

            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(orderConfirmedEventKafkaTemplate);
        }

        @Test
        @DisplayName("a duplicated event does not confirm an already confirmed order twice")
        void shouldBeIdempotent() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.CONFIRMED)));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "SUCCESS"));

            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(orderConfirmedEventKafkaTemplate);
        }

        @Test
        @DisplayName("a cancelled order is never resurrected by a late payment")
        void shouldNeverResurrectACancelledOrder() {
            Order cancelled = TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.CANCELLED);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(cancelled));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "SUCCESS"));

            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderRepository, never()).save(any(Order.class));
            verifyNoInteractions(orderConfirmedEventKafkaTemplate);
        }

        @Test
        @DisplayName("a delivered order is left alone")
        void shouldLeaveADeliveredOrderAlone() {
            when(orderRepository.findById(ORDER_ID))
                    .thenReturn(Optional.of(TestDataFactory.order(ORDER_ID, TestDataFactory.USER_ID, OrderStatus.DELIVERED)));

            consumer.handlePaymentSuccess(TestDataFactory.paymentSuccessEvent(ORDER_ID, "SUCCESS"));

            verify(orderConfirmedEventKafkaTemplate, never()).send(anyString(), any(OrderConfirmedEvent.class));
        }
    }

    // ------------------------------------------------------------------
    // wiring
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("wiring")
    class Wiring {

        private Method handler() throws Exception {
            return PaymentSuccessConsumer.class.getMethod("handlePaymentSuccess", PaymentSuccessEvent.class);
        }

        @Test
        @DisplayName("listens on the payment-success topic")
        void listensOnThePaymentSuccessTopic() throws Exception {
            KafkaListener listener = handler().getAnnotation(KafkaListener.class);

            assertThat(listener).isNotNull();
            assertThat(listener.topics()).containsExactly("payment-success-topic");
        }

        @Test
        @DisplayName("handles one event per invocation, inside a transaction")
        void handlesOneEventInsideATransaction() throws Exception {
            assertThat(handler().getAnnotation(Transactional.class)).isNotNull();
            assertThat(handler().getParameterTypes()).containsExactly(PaymentSuccessEvent.class);
            assertThat(handler().getReturnType()).isEqualTo(void.class);
        }

        @Test
        @DisplayName("is a Spring bean")
        void isASpringBean() {
            assertThat(PaymentSuccessConsumer.class.getAnnotation(org.springframework.stereotype.Service.class))
                    .isNotNull();
        }
    }
}


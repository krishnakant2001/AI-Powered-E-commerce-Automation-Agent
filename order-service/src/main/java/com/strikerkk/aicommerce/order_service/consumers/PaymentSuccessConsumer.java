package com.strikerkk.aicommerce.order_service.consumers;

import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.event.OrderConfirmedEvent;
import com.strikerkk.aicommerce.order_service.event.OrderPlacedItem;
import com.strikerkk.aicommerce.order_service.repository.OrderRepository;
import com.strikerkk.aicommerce.payment_service.event.PaymentSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentSuccessConsumer {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<Long, OrderConfirmedEvent> orderConfirmedEventKafkaTemplate;

    @Transactional
    @KafkaListener(topics = "payment-success-topic")
    public void handlePaymentSuccess(PaymentSuccessEvent event) {

        String SuccessStatus = "SUCCESS";

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);

        if(order == null) {
            log.error("Order not found for orderId: {}", event.getOrderId());
            return;
        }

        log.info("Processing payment success for orderId: {}", event.getOrderId());

        if(SuccessStatus.equals(event.getPaymentStatus()) && order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            OrderConfirmedEvent orderConfirmedEvent = OrderConfirmedEvent.builder()
                    .orderId(order.getId())
                    .orderPlacedItems(getOrderPlacedItems(order))
                    .build();

            orderConfirmedEventKafkaTemplate.send("order-confirmed-topic", orderConfirmedEvent);
        }
    }

    private List<OrderPlacedItem> getOrderPlacedItems(Order placedOrder) {
        return placedOrder.getOrderItems()
                .stream()
                .map(orderItem -> OrderPlacedItem.builder()
                        .id(orderItem.getId())
                        .productId(orderItem.getProductId())
                        .variantId(orderItem.getVariantId())
                        .quantity(orderItem.getQuantity())
                        .build())
                .toList();
    }

}

package com.strikerkk.aicommerce.order_service.repository;

import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.entity.OrderItem;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("OrderItemRepository")
class OrderItemRepositoryTest {

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Order persistedOrder() {
        return orderRepository.saveAndFlush(Order.builder()
                .userId(42L)
                .addressId(7L)
                .totalAmount(new BigDecimal("499.00"))
                .deliveryCharges(new BigDecimal("40.00"))
                .needToPay(new BigDecimal("539.00"))
                .status(OrderStatus.PENDING)
                .orderItems(new ArrayList<>())
                .build());
    }

    private OrderItem item(Order order) {
        return OrderItem.builder()
                .order(order)
                .productId(1L)
                .variantId(2L)
                .quantity(2)
                .productName("JBL Flip 6")
                .productBrand("JBL")
                .productImageUrl("products/1/primary.png")
                .size("M")
                .color("Black")
                .priceAtOrder(new BigDecimal("499.00"))
                .lineTotal(new BigDecimal("998.00"))
                .build();
    }

    @Test
    @DisplayName("persists a line and generates its id")
    void shouldPersistALine() {
        OrderItem saved = orderItemRepository.saveAndFlush(item(persistedOrder()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("keeps the whole product snapshot")
    void shouldKeepTheWholeSnapshot() {
        OrderItem saved = orderItemRepository.saveAndFlush(item(persistedOrder()));
        entityManager.clear();

        OrderItem reloaded = orderItemRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getProductId()).isEqualTo(1L);
        assertThat(reloaded.getVariantId()).isEqualTo(2L);
        assertThat(reloaded.getProductName()).isEqualTo("JBL Flip 6");
        assertThat(reloaded.getProductBrand()).isEqualTo("JBL");
        assertThat(reloaded.getProductImageUrl()).isEqualTo("products/1/primary.png");
        assertThat(reloaded.getSize()).isEqualTo("M");
        assertThat(reloaded.getColor()).isEqualTo("Black");
        assertThat(reloaded.getPriceAtOrder()).isEqualByComparingTo("499.00");
        assertThat(reloaded.getLineTotal()).isEqualByComparingTo("998.00");
    }

    @Test
    @DisplayName("points back at its order")
    void shouldPointBackAtItsOrder() {
        Order order = persistedOrder();
        OrderItem saved = orderItemRepository.saveAndFlush(item(order));
        entityManager.clear();

        OrderItem reloaded = orderItemRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getOrder().getId()).isEqualTo(order.getId());
    }

    @Test
    @DisplayName("refuses a line that belongs to no order")
    void shouldRefuseAnOrphanLine() {
        OrderItem orphan = item(null);

        assertThatThrownBy(() -> orderItemRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refuses a line without a product")
    void shouldRefuseALineWithoutAProduct() {
        OrderItem item = item(persistedOrder());
        item.setProductId(null);

        assertThatThrownBy(() -> orderItemRepository.saveAndFlush(item))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refuses a line without a quantity")
    void shouldRefuseALineWithoutAQuantity() {
        OrderItem item = item(persistedOrder());
        item.setQuantity(null);

        assertThatThrownBy(() -> orderItemRepository.saveAndFlush(item))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refuses a line without its price snapshot")
    void shouldRefuseALineWithoutItsPrice() {
        OrderItem item = item(persistedOrder());
        item.setPriceAtOrder(null);

        assertThatThrownBy(() -> orderItemRepository.saveAndFlush(item))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("stores several lines for the same order")
    void shouldStoreSeveralLinesOfTheSameOrder() {
        Order order = persistedOrder();

        orderItemRepository.saveAndFlush(item(order));
        OrderItem second = item(order);
        second.setProductId(9L);
        second.setVariantId(8L);
        orderItemRepository.saveAndFlush(second);

        assertThat(orderItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("deletes a line without touching its order")
    void shouldDeleteALineWithoutTouchingItsOrder() {
        Order order = persistedOrder();
        OrderItem saved = orderItemRepository.saveAndFlush(item(order));

        orderItemRepository.delete(saved);
        orderItemRepository.flush();
        entityManager.clear();

        assertThat(orderItemRepository.findById(saved.getId())).isEmpty();
        assertThat(orderRepository.findById(order.getId())).isPresent();
    }

    @Test
    @DisplayName("keeps an updated quantity")
    void shouldKeepAnUpdatedQuantity() {
        OrderItem saved = orderItemRepository.saveAndFlush(item(persistedOrder()));

        saved.setQuantity(5);
        saved.setLineTotal(new BigDecimal("2495.00"));
        orderItemRepository.saveAndFlush(saved);
        entityManager.clear();

        OrderItem reloaded = orderItemRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(5);
        assertThat(reloaded.getLineTotal()).isEqualByComparingTo("2495.00");
    }
}


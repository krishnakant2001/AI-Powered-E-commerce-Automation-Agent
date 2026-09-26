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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("OrderRepository")
class OrderRepositoryTest {

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Order order(Long userId, OrderStatus status, String total) {
        return Order.builder()
                .userId(userId)
                .addressId(7L)
                .totalAmount(new BigDecimal(total))
                .deliveryCharges(new BigDecimal("40.00"))
                .needToPay(new BigDecimal(total).add(new BigDecimal("40.00")))
                .status(status)
                .orderItems(new ArrayList<>())
                .build();
    }

    private OrderItem item(Order order, Long productId, Long variantId, int quantity, String price) {
        return OrderItem.builder()
                .order(order)
                .productId(productId)
                .variantId(variantId)
                .quantity(quantity)
                .productName("JBL Flip 6")
                .productBrand("JBL")
                .productImageUrl("products/1/primary.png")
                .size("M")
                .color("Black")
                .priceAtOrder(new BigDecimal(price))
                .lineTotal(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    private Order orderWithOneLine(Long userId, OrderStatus status) {
        Order order = order(userId, status, "499.00");
        order.getOrderItems().add(item(order, 1L, 2L, 1, "499.00"));
        return order;
    }

    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------

    @Test
    @DisplayName("persists an order and generates its id")
    void shouldPersistAnOrder() {
        Order saved = orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "499.00"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("stamps created_at and updated_at through the Hibernate callbacks")
    void shouldStampBothTimestamps() {
        Order saved = orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "499.00"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("keeps the amounts to the paisa")
    void shouldKeepTheAmounts() {
        Order saved = orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "1234.56"));
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("1234.56");
        assertThat(reloaded.getDeliveryCharges()).isEqualByComparingTo("40.00");
        assertThat(reloaded.getNeedToPay()).isEqualByComparingTo("1274.56");
    }

    @Test
    @DisplayName("stores the status as readable text")
    void shouldStoreTheStatusAsText() {
        Order saved = orderRepository.saveAndFlush(order(USER_ID, OrderStatus.CONFIRMED, "499.00"));
        entityManager.flush();

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("select order_status from orders where id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        assertThat(stored).hasToString("CONFIRMED");
    }

    @Test
    @DisplayName("cascades the lines when the order is saved")
    void shouldCascadeTheLines() {
        Order saved = orderRepository.saveAndFlush(orderWithOneLine(USER_ID, OrderStatus.PENDING));
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getOrderItems()).hasSize(1);
        assertThat(reloaded.getOrderItems().getFirst().getId()).isNotNull();
        assertThat(reloaded.getOrderItems().getFirst().getProductName()).isEqualTo("JBL Flip 6");
        assertThat(reloaded.getOrderItems().getFirst().getLineTotal()).isEqualByComparingTo("499.00");
    }

    @Test
    @DisplayName("cascades the delete, no line ever outlives its order")
    void shouldCascadeTheDelete() {
        Order saved = orderRepository.saveAndFlush(orderWithOneLine(USER_ID, OrderStatus.PENDING));
        Long lineId = saved.getOrderItems().getFirst().getId();

        orderRepository.delete(saved);
        orderRepository.flush();
        entityManager.clear();

        assertThat(entityManager.find(OrderItem.class, lineId)).isNull();
    }

    @Test
    @DisplayName("removes an orphaned line from the table")
    void shouldRemoveAnOrphanedLine() {
        Order saved = orderRepository.saveAndFlush(orderWithOneLine(USER_ID, OrderStatus.PENDING));
        Long lineId = saved.getOrderItems().getFirst().getId();

        saved.getOrderItems().clear();
        orderRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(entityManager.find(OrderItem.class, lineId)).isNull();
    }

    @Test
    @DisplayName("refuses an order without an owner")
    void shouldRefuseAnOrderWithoutAnOwner() {
        Order order = order(null, OrderStatus.PENDING, "499.00");

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refuses an order without an address")
    void shouldRefuseAnOrderWithoutAnAddress() {
        Order order = order(USER_ID, OrderStatus.PENDING, "499.00");
        order.setAddressId(null);

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refuses an order without a status")
    void shouldRefuseAnOrderWithoutAStatus() {
        Order order = order(USER_ID, null, "499.00");

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refuses an order without the amount to pay")
    void shouldRefuseAnOrderWithoutTheAmount() {
        Order order = order(USER_ID, OrderStatus.PENDING, "499.00");
        order.setNeedToPay(null);

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // findByIdAndUserId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("finds the order of its owner")
    void shouldFindTheOrderOfItsOwner() {
        Order saved = orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "499.00"));
        entityManager.clear();

        Optional<Order> found = orderRepository.findByIdAndUserId(saved.getId(), USER_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("hides the order from anybody else")
    void shouldHideTheOrderFromAnybodyElse() {
        Order saved = orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "499.00"));
        entityManager.clear();

        assertThat(orderRepository.findByIdAndUserId(saved.getId(), OTHER_USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("returns nothing for an id that does not exist")
    void shouldReturnNothingForAnUnknownId() {
        assertThat(orderRepository.findByIdAndUserId(404L, USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("brings the lines along")
    void shouldBringTheLinesAlong() {
        Order saved = orderRepository.saveAndFlush(orderWithOneLine(USER_ID, OrderStatus.PENDING));
        entityManager.clear();

        Order found = orderRepository.findByIdAndUserId(saved.getId(), USER_ID).orElseThrow();

        assertThat(found.getOrderItems()).hasSize(1);
    }

    // ------------------------------------------------------------------
    // findAllByUserId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("lists every order of a user")
    void shouldListEveryOrderOfAUser() {
        orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "100.00"));
        orderRepository.saveAndFlush(order(USER_ID, OrderStatus.DELIVERED, "200.00"));
        entityManager.clear();

        assertThat(orderRepository.findAllByUserId(USER_ID)).hasSize(2);
    }

    @Test
    @DisplayName("never mixes the orders of two users")
    void shouldNeverMixTwoUsers() {
        orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "100.00"));
        orderRepository.saveAndFlush(order(OTHER_USER_ID, OrderStatus.PENDING, "200.00"));
        entityManager.clear();

        assertThat(orderRepository.findAllByUserId(USER_ID))
                .hasSize(1)
                .allSatisfy(order -> assertThat(order.getUserId()).isEqualTo(USER_ID));
    }

    @Test
    @DisplayName("returns an empty list for a user who never ordered")
    void shouldReturnAnEmptyList() {
        assertThat(orderRepository.findAllByUserId(123456L)).isEmpty();
    }

    @Test
    @DisplayName("lists orders of every status, cancelled ones included")
    void shouldListOrdersOfEveryStatus() {
        orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "100.00"));
        orderRepository.saveAndFlush(order(USER_ID, OrderStatus.CANCELLED, "200.00"));
        orderRepository.saveAndFlush(order(USER_ID, OrderStatus.DELIVERED, "300.00"));
        entityManager.clear();

        List<Order> orders = orderRepository.findAllByUserId(USER_ID);

        assertThat(orders)
                .extracting(Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.CANCELLED, OrderStatus.DELIVERED);
    }

    // ------------------------------------------------------------------
    // updates
    // ------------------------------------------------------------------

    @Test
    @DisplayName("keeps a status change")
    void shouldKeepAStatusChange() {
        Order saved = orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "499.00"));

        saved.setStatus(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(orderRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("counts what it stored")
    void shouldCountWhatItStored() {
        orderRepository.saveAndFlush(order(USER_ID, OrderStatus.PENDING, "100.00"));
        orderRepository.saveAndFlush(order(OTHER_USER_ID, OrderStatus.PENDING, "200.00"));

        assertThat(orderRepository.count()).isEqualTo(2);
    }
}


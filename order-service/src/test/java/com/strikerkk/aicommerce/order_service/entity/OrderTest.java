package com.strikerkk.aicommerce.order_service.entity;

import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Order")
class OrderTest {

    private Field field(String name) throws Exception {
        return Order.class.getDeclaredField(name);
    }

    // ------------------------------------------------------------------
    // behaviour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("is built with every amount of the checkout")
    void isBuiltWithEveryAmount() {
        Order order = Order.builder()
                .id(1L)
                .userId(TestDataFactory.USER_ID)
                .addressId(TestDataFactory.ADDRESS_ID)
                .totalAmount(new BigDecimal("499.00"))
                .deliveryCharges(new BigDecimal("40"))
                .needToPay(new BigDecimal("539.00"))
                .status(OrderStatus.PENDING)
                .build();

        assertThat(order.getId()).isEqualTo(1L);
        assertThat(order.getUserId()).isEqualTo(TestDataFactory.USER_ID);
        assertThat(order.getAddressId()).isEqualTo(TestDataFactory.ADDRESS_ID);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("499.00");
        assertThat(order.getDeliveryCharges()).isEqualByComparingTo("40");
        assertThat(order.getNeedToPay()).isEqualByComparingTo("539.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("starts with an empty, never null, line list")
    void startsWithAnEmptyLineList() {
        assertThat(Order.builder().build().getOrderItems()).isNotNull().isEmpty();
        // The field initializer also runs for the constructor Hibernate uses.
        assertThat(new Order().getOrderItems()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("accepts the lines of the cart")
    void acceptsTheLinesOfTheCart() {
        Order order = Order.builder().orderItems(new ArrayList<>()).build();
        OrderItem item = TestDataFactory.orderItem();

        order.getOrderItems().add(item);
        item.setOrder(order);

        assertThat(order.getOrderItems()).containsExactly(item);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    @DisplayName("moves through its life cycle by simply changing the status")
    void movesThroughItsLifeCycle() {
        Order order = TestDataFactory.order();

        order.setStatus(OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        order.setStatus(OrderStatus.SHIPPED);
        order.setStatus(OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("keeps the full precision of an amount")
    void keepsTheFullPrecision() {
        Order order = Order.builder().totalAmount(new BigDecimal("1234.56")).build();

        assertThat(order.getTotalAmount().scale()).isEqualTo(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1234.56");
    }

    @Test
    @DisplayName("has a no args constructor for Hibernate and an all args one for the builder")
    void hasBothConstructors() throws Exception {
        assertThat(Order.class.getDeclaredConstructor()).isNotNull();
        assertThat(Order.class.getDeclaredConstructors()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("lets the timestamps be set by Hibernate, not by hand")
    void lestTheTimestampsBeSetByHibernate() {
        Order order = Order.builder().build();

        assertThat(order.getCreatedAt()).isNull();
        assertThat(order.getUpdatedAt()).isNull();

        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        assertThat(order.getCreatedAt()).isEqualTo(now);
    }

    // ------------------------------------------------------------------
    // mapping
    // ------------------------------------------------------------------

    @Test
    @DisplayName("maps onto the 'orders' table")
    void mapsOntoTheOrdersTable() {
        assertThat(Order.class.getAnnotation(Entity.class)).isNotNull();
        assertThat(Order.class.getAnnotation(Table.class).name()).isEqualTo("orders");
    }

    @Test
    @DisplayName("lets the database generate the id")
    void letsTheDatabaseGenerateTheId() throws Exception {
        assertThat(field("id").getAnnotation(Id.class)).isNotNull();
        assertThat(field("id").getAnnotation(GeneratedValue.class).strategy())
                .isEqualTo(GenerationType.IDENTITY);
    }

    @Test
    @DisplayName("stores the owner and the address as mandatory columns")
    void storesTheOwnerAndTheAddress() throws Exception {
        assertThat(field("userId").getAnnotation(Column.class).name()).isEqualTo("user_id");
        assertThat(field("userId").getAnnotation(Column.class).nullable()).isFalse();
        assertThat(field("addressId").getAnnotation(Column.class).name()).isEqualTo("address_id");
        assertThat(field("addressId").getAnnotation(Column.class).nullable()).isFalse();
    }

    @Test
    @DisplayName("stores every amount with a precision of 10 and a scale of 2")
    void storesEveryAmountWithMoneyPrecision() throws Exception {
        for (String name : new String[]{"totalAmount", "deliveryCharges", "needToPay"}) {
            Column column = field(name).getAnnotation(Column.class);
            assertThat(column.precision()).as(name).isEqualTo(10);
            assertThat(column.scale()).as(name).isEqualTo(2);
            assertThat(column.nullable()).as(name).isFalse();
        }
    }

    @Test
    @DisplayName("stores the status as readable text in order_status")
    void storesTheStatusAsText() throws Exception {
        assertThat(field("status").getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);

        Column column = field("status").getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo("order_status");
        assertThat(column.nullable()).isFalse();
        assertThat(column.length()).isEqualTo(20);
    }

    @Test
    @DisplayName("owns its lines - they are cascaded, orphan removed and lazy")
    void ownsItsLines() throws Exception {
        OneToMany association = field("orderItems").getAnnotation(OneToMany.class);

        assertThat(association.mappedBy()).isEqualTo("order");
        assertThat(association.cascade()).containsExactly(CascadeType.ALL);
        assertThat(association.orphanRemoval()).isTrue();
        assertThat(association.fetch()).isEqualTo(FetchType.LAZY);
    }

    @Test
    @DisplayName("stamps created_at and updated_at through Hibernate")
    void stampsBothTimestamps() throws Exception {
        assertThat(field("createdAt").getAnnotation(CreationTimestamp.class)).isNotNull();
        assertThat(field("createdAt").getAnnotation(Column.class).nullable()).isFalse();
        assertThat(field("updatedAt").getAnnotation(UpdateTimestamp.class)).isNotNull();
        assertThat(field("updatedAt").getAnnotation(Column.class).nullable()).isFalse();
    }
}



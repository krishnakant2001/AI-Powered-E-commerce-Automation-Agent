package com.strikerkk.aicommerce.order_service.entity;

import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderItem")
class OrderItemTest {

    private Field field(String name) throws Exception {
        return OrderItem.class.getDeclaredField(name);
    }

    // ------------------------------------------------------------------
    // behaviour
    // ------------------------------------------------------------------

    @Test
    @DisplayName("snapshots the product, so a later price change never rewrites history")
    void snapshotsTheProduct() {
        OrderItem item = TestDataFactory.orderItem();

        assertThat(item.getProductId()).isEqualTo(TestDataFactory.PRODUCT_ID);
        assertThat(item.getVariantId()).isEqualTo(TestDataFactory.VARIANT_ID);
        assertThat(item.getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
        assertThat(item.getProductBrand()).isEqualTo(TestDataFactory.BRAND);
        assertThat(item.getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
        assertThat(item.getSize()).isEqualTo(TestDataFactory.SIZE);
        assertThat(item.getColor()).isEqualTo(TestDataFactory.COLOR);
        assertThat(item.getPriceAtOrder()).isEqualByComparingTo(TestDataFactory.PRICE);
    }

    @Test
    @DisplayName("carries the quantity and the total of the line")
    void carriesTheQuantityAndTheTotal() {
        OrderItem item = TestDataFactory.orderItem(1L, null, 1L, 2L, 3, new BigDecimal("100.00"));

        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getLineTotal()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("points back at the order it belongs to")
    void pointsBackAtItsOrder() {
        Order order = TestDataFactory.order();
        OrderItem item = TestDataFactory.orderItem();

        item.setOrder(order);

        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    @DisplayName("is free floating until it is attached to an order")
    void isFreeFloatingUntilAttached() {
        assertThat(TestDataFactory.orderItem().getOrder()).isNull();
        assertThat(new OrderItem().getId()).isNull();
    }

    @Test
    @DisplayName("stays mutable through its setters")
    void staysMutable() {
        OrderItem item = new OrderItem();

        item.setProductId(9L);
        item.setVariantId(8L);
        item.setQuantity(2);
        item.setPriceAtOrder(new BigDecimal("10.00"));
        item.setLineTotal(new BigDecimal("20.00"));

        assertThat(item.getProductId()).isEqualTo(9L);
        assertThat(item.getVariantId()).isEqualTo(8L);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getLineTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("has a no args constructor for Hibernate and an all args one for the builder")
    void hasBothConstructors() throws Exception {
        assertThat(OrderItem.class.getDeclaredConstructor()).isNotNull();
        assertThat(OrderItem.class.getDeclaredConstructors()).hasSizeGreaterThanOrEqualTo(2);
    }

    // ------------------------------------------------------------------
    // mapping
    // ------------------------------------------------------------------

    @Test
    @DisplayName("maps onto the 'order_items' table")
    void mapsOntoTheOrderItemsTable() {
        assertThat(OrderItem.class.getAnnotation(Entity.class)).isNotNull();
        assertThat(OrderItem.class.getAnnotation(Table.class).name()).isEqualTo("order_items");
    }

    @Test
    @DisplayName("lets the database generate the id")
    void letsTheDatabaseGenerateTheId() throws Exception {
        assertThat(field("id").getAnnotation(Id.class)).isNotNull();
        assertThat(field("id").getAnnotation(GeneratedValue.class).strategy())
                .isEqualTo(GenerationType.IDENTITY);
    }

    @Test
    @DisplayName("joins its order lazily through a mandatory order_id")
    void joinsItsOrderLazily() throws Exception {
        assertThat(field("order").getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.LAZY);

        JoinColumn joinColumn = field("order").getAnnotation(JoinColumn.class);
        assertThat(joinColumn.name()).isEqualTo("order_id");
        assertThat(joinColumn.nullable()).isFalse();
    }

    @Test
    @DisplayName("stores every snapshot column, and none of them may be null")
    void storesEverySnapshotColumn() throws Exception {
        String[][] columns = {
                {"productId", "product_id"},
                {"variantId", "variant_id"},
                {"quantity", "quantity"},
                {"productName", "product_name"},
                {"productBrand", "product_brand"},
                {"productImageUrl", "product_image_url"},
                {"size", "size"},
                {"color", "color"},
        };

        for (String[] column : columns) {
            Column annotation = field(column[0]).getAnnotation(Column.class);
            assertThat(annotation.name()).as(column[0]).isEqualTo(column[1]);
            assertThat(annotation.nullable()).as(column[0]).isFalse();
        }
    }

    @Test
    @DisplayName("stores both amounts with a precision of 10 and a scale of 2")
    void storesBothAmountsWithMoneyPrecision() throws Exception {
        for (String name : new String[]{"priceAtOrder", "lineTotal"}) {
            Column column = field(name).getAnnotation(Column.class);
            assertThat(column.precision()).as(name).isEqualTo(10);
            assertThat(column.scale()).as(name).isEqualTo(2);
            assertThat(column.nullable()).as(name).isFalse();
        }
    }

    @Test
    @DisplayName("carries no timestamps of its own - the order owns them")
    void carriesNoTimestamps() {
        assertThat(OrderItem.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("createdAt", "updatedAt");
    }
}


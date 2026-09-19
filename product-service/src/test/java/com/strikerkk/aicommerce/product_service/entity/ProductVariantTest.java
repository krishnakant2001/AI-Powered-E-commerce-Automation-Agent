package com.strikerkk.aicommerce.product_service.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductVariant entity")
class ProductVariantTest {

    private Product parent() {
        return Product.builder().id(1L).name("JBL Flip 6").createdBy("admin-1").build();
    }

    @Test
    @DisplayName("the builder copies every field")
    void builderCopiesEveryField() {
        Product product = parent();

        ProductVariant variant = ProductVariant.builder()
                .id(100L)
                .product(product)
                .size("M")
                .color("Black")
                .stockCount(10)
                .priceOverride(new BigDecimal("9499.00"))
                .build();

        assertThat(variant.getId()).isEqualTo(100L);
        assertThat(variant.getProduct()).isSameAs(product);
        assertThat(variant.getSize()).isEqualTo("M");
        assertThat(variant.getColor()).isEqualTo("Black");
        assertThat(variant.getStockCount()).isEqualTo(10);
        assertThat(variant.getPriceOverride()).isEqualByComparingTo("9499.00");
    }

    @Test
    @DisplayName("size, colour and price override are optional")
    void optionalFieldsMayStayNull() {
        ProductVariant variant = ProductVariant.builder()
                .product(parent())
                .stockCount(0)
                .build();

        assertThat(variant.getSize()).isNull();
        assertThat(variant.getColor()).isNull();
        assertThat(variant.getPriceOverride()).isNull();
        assertThat(variant.getStockCount()).isZero();
    }

    @Test
    @DisplayName("the no-args constructor leaves every field null - required by JPA")
    void noArgsConstructorLeavesEverythingNull() {
        ProductVariant variant = new ProductVariant();

        assertThat(variant.getId()).isNull();
        assertThat(variant.getProduct()).isNull();
        assertThat(variant.getStockCount()).isNull();
        assertThat(variant.getPriceOverride()).isNull();
    }

    @Test
    @DisplayName("the all-args constructor keeps the declared field order")
    void allArgsConstructorKeepsFieldOrder() {
        Product product = parent();
        ProductVariant variant =
                new ProductVariant(5L, product, "XL", "Blue", 42, new BigDecimal("12.00"));

        assertThat(variant.getId()).isEqualTo(5L);
        assertThat(variant.getProduct()).isSameAs(product);
        assertThat(variant.getSize()).isEqualTo("XL");
        assertThat(variant.getColor()).isEqualTo("Blue");
        assertThat(variant.getStockCount()).isEqualTo(42);
        assertThat(variant.getPriceOverride()).isEqualByComparingTo("12.00");
    }

    @Test
    @DisplayName("every setter used by the update and the stock flows is available")
    void settersAreAvailable() {
        ProductVariant variant = new ProductVariant();

        variant.setSize("S");
        variant.setColor("Red");
        variant.setStockCount(3);
        variant.setPriceOverride(new BigDecimal("99.99"));

        assertThat(variant.getSize()).isEqualTo("S");
        assertThat(variant.getColor()).isEqualTo("Red");
        assertThat(variant.getStockCount()).isEqualTo(3);
        assertThat(variant.getPriceOverride()).isEqualByComparingTo("99.99");
    }

    @Test
    @DisplayName("the stock count can be decremented, as the order-confirmed consumer does")
    void stockCountCanBeDecremented() {
        ProductVariant variant = ProductVariant.builder().stockCount(10).build();

        variant.setStockCount(variant.getStockCount() - 4);

        assertThat(variant.getStockCount()).isEqualTo(6);
    }
}


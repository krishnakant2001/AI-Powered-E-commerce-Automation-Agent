package com.strikerkk.aicommerce.product_service.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Product entity")
class ProductTest {

    private Product newProduct() {
        return Product.builder()
                .id(1L)
                .name("JBL Flip 6")
                .brand("JBL")
                .description("Portable speaker")
                .price(new BigDecimal("8999.00"))
                .category("Speakers")
                .stockCount(25)
                .isAvailable(true)
                .createdBy("admin-1")
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("the builder copies every field")
    void builderCopiesEveryField() {
        Product product = newProduct();

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("JBL Flip 6");
        assertThat(product.getBrand()).isEqualTo("JBL");
        assertThat(product.getDescription()).isEqualTo("Portable speaker");
        assertThat(product.getPrice()).isEqualByComparingTo("8999.00");
        assertThat(product.getCategory()).isEqualTo("Speakers");
        assertThat(product.getStockCount()).isEqualTo(25);
        assertThat(product.getIsAvailable()).isTrue();
        assertThat(product.getCreatedBy()).isEqualTo("admin-1");
        assertThat(product.getVariants()).isEmpty();
        assertThat(product.getImages()).isEmpty();
    }

    @Test
    @DisplayName("the no-args constructor leaves every field null - required by JPA")
    void noArgsConstructorLeavesEverythingNull() {
        Product product = new Product();

        assertThat(product.getId()).isNull();
        assertThat(product.getName()).isNull();
        assertThat(product.getPrice()).isNull();
        assertThat(product.getStockCount()).isNull();
        assertThat(product.getIsAvailable()).isNull();
        assertThat(product.getCreatedAt()).isNull();
        assertThat(product.getVariants()).isNull();
        assertThat(product.getImages()).isNull();
    }

    @Test
    @DisplayName("the all-args constructor keeps the declared field order")
    void allArgsConstructorKeepsFieldOrder() {
        LocalDateTime createdAt = LocalDateTime.now();
        List<ProductVariant> variants = new ArrayList<>();
        List<ProductImage> images = new ArrayList<>();

        Product product = new Product(
                2L, "Charge 5", "JBL", "desc", new BigDecimal("1.00"), "Audio",
                3, false, "admin-2", createdAt, variants, images);

        assertThat(product.getId()).isEqualTo(2L);
        assertThat(product.getName()).isEqualTo("Charge 5");
        assertThat(product.getBrand()).isEqualTo("JBL");
        assertThat(product.getDescription()).isEqualTo("desc");
        assertThat(product.getCategory()).isEqualTo("Audio");
        assertThat(product.getStockCount()).isEqualTo(3);
        assertThat(product.getIsAvailable()).isFalse();
        assertThat(product.getCreatedBy()).isEqualTo("admin-2");
        assertThat(product.getCreatedAt()).isEqualTo(createdAt);
        assertThat(product.getVariants()).isSameAs(variants);
        assertThat(product.getImages()).isSameAs(images);
    }

    @Test
    @DisplayName("@PrePersist stamps createdAt")
    void prePersistStampsCreatedAt() {
        Product product = newProduct();
        assertThat(product.getCreatedAt()).isNull();

        LocalDateTime before = LocalDateTime.now();
        product.onCreate(); // the @PrePersist callback, reachable from the same package
        LocalDateTime after = LocalDateTime.now();

        assertThat(product.getCreatedAt()).isNotNull()
                .isBetween(before, after);
    }

    @Test
    @DisplayName("every setter is available for the update flow")
    void settersAreAvailable() {
        Product product = new Product();

        product.setName("name");
        product.setBrand("brand");
        product.setDescription("description");
        product.setPrice(new BigDecimal("10.50"));
        product.setCategory("category");
        product.setStockCount(7);
        product.setIsAvailable(false);
        product.setCreatedBy("admin-3");

        assertThat(product.getName()).isEqualTo("name");
        assertThat(product.getBrand()).isEqualTo("brand");
        assertThat(product.getDescription()).isEqualTo("description");
        assertThat(product.getPrice()).isEqualByComparingTo("10.50");
        assertThat(product.getCategory()).isEqualTo("category");
        assertThat(product.getStockCount()).isEqualTo(7);
        assertThat(product.getIsAvailable()).isFalse();
        assertThat(product.getCreatedBy()).isEqualTo("admin-3");
    }

    @Test
    @DisplayName("the variant and image collections are mutable")
    void collectionsAreMutable() {
        Product product = newProduct();

        product.getVariants().add(ProductVariant.builder().product(product).stockCount(1).build());
        product.getImages().add(ProductImage.builder().product(product).url("k").isPrimary(true).build());

        assertThat(product.getVariants()).hasSize(1);
        assertThat(product.getImages()).hasSize(1);
        assertThat(product.getVariants().getFirst().getProduct()).isSameAs(product);
        assertThat(product.getImages().getFirst().getProduct()).isSameAs(product);
    }
}


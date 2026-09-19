package com.strikerkk.aicommerce.product_service.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductImage entity")
class ProductImageTest {

    private Product parent() {
        return Product.builder().id(1L).name("JBL Flip 6").createdBy("admin-1").build();
    }

    @Test
    @DisplayName("the builder copies every field")
    void builderCopiesEveryField() {
        Product product = parent();

        ProductImage image = ProductImage.builder()
                .id(200L)
                .product(product)
                .url("products/1/uuid-flip6.png")
                .isPrimary(true)
                .build();

        assertThat(image.getId()).isEqualTo(200L);
        assertThat(image.getProduct()).isSameAs(product);
        assertThat(image.getUrl()).isEqualTo("products/1/uuid-flip6.png");
        assertThat(image.getIsPrimary()).isTrue();
    }

    @Test
    @DisplayName("the url column holds the S3 object key, not a fully qualified URL")
    void urlHoldsTheS3Key() {
        ProductImage image = ProductImage.builder()
                .product(parent())
                .url("products/1/8f1b2c3d-flip6.png")
                .isPrimary(false)
                .build();

        assertThat(image.getUrl())
                .doesNotStartWith("http")
                .startsWith("products/");
    }

    @Test
    @DisplayName("the no-args constructor leaves every field null - required by JPA")
    void noArgsConstructorLeavesEverythingNull() {
        ProductImage image = new ProductImage();

        assertThat(image.getId()).isNull();
        assertThat(image.getProduct()).isNull();
        assertThat(image.getUrl()).isNull();
        assertThat(image.getIsPrimary()).isNull();
    }

    @Test
    @DisplayName("the all-args constructor keeps the declared field order")
    void allArgsConstructorKeepsFieldOrder() {
        Product product = parent();
        ProductImage image = new ProductImage(9L, product, "products/1/key.png", false);

        assertThat(image.getId()).isEqualTo(9L);
        assertThat(image.getProduct()).isSameAs(product);
        assertThat(image.getUrl()).isEqualTo("products/1/key.png");
        assertThat(image.getIsPrimary()).isFalse();
    }

    @Test
    @DisplayName("the url and the primary flag can be swapped by the update flow")
    void settersAreAvailable() {
        ProductImage image = new ProductImage();

        image.setUrl("products/1/new-key.png");
        image.setIsPrimary(true);

        assertThat(image.getUrl()).isEqualTo("products/1/new-key.png");
        assertThat(image.getIsPrimary()).isTrue();
    }
}


package com.strikerkk.aicommerce.product_service.repository;

import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("ProductImageRepository")
class ProductImageRepositoryTest {

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Product product;
    private Product otherProduct;

    @BeforeEach
    void setUp() {
        product = productRepository.save(product("JBL Flip 6", "admin-1"));
        otherProduct = productRepository.save(product("JBL Charge 5", "admin-2"));
        entityManager.flush();
    }

    private Product product(String name, String createdBy) {
        return Product.builder()
                .name(name)
                .brand("JBL")
                .price(new BigDecimal("8999.00"))
                .category("Speakers")
                .stockCount(25)
                .isAvailable(true)
                .createdBy(createdBy)
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    private ProductImage image(Product parent, String key, boolean isPrimary) {
        return ProductImage.builder()
                .product(parent)
                .url(key)
                .isPrimary(isPrimary)
                .build();
    }

    @Test
    @DisplayName("persists an image and generates its id")
    void shouldPersistImage() {
        ProductImage saved =
                productImageRepository.saveAndFlush(image(product, "products/1/a.png", true));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUrl()).isEqualTo("products/1/a.png");
        assertThat(saved.getIsPrimary()).isTrue();
        assertThat(saved.getProduct().getId()).isEqualTo(product.getId());
    }

    @Test
    @DisplayName("rejects an image that is not attached to a product")
    void shouldRejectImageWithoutProduct() {
        ProductImage orphan = image(null, "products/1/a.png", true);

        assertThatThrownBy(() -> productImageRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects an image without a key")
    void shouldRejectImageWithoutUrl() {
        ProductImage invalid = image(product, null, true);

        assertThatThrownBy(() -> productImageRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects an image without the primary flag")
    void shouldRejectImageWithoutPrimaryFlag() {
        ProductImage invalid = image(product, "products/1/a.png", true);
        invalid.setIsPrimary(null);

        assertThatThrownBy(() -> productImageRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByIdAndProductId returns the image when it belongs to the product")
    void shouldFindByIdAndProductId() {
        ProductImage saved =
                productImageRepository.saveAndFlush(image(product, "products/1/a.png", true));
        entityManager.clear();

        Optional<ProductImage> found =
                productImageRepository.findByIdAndProductId(saved.getId(), product.getId());

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getUrl()).isEqualTo("products/1/a.png");
    }

    @Test
    @DisplayName("findByIdAndProductId returns empty when the image belongs to another product")
    void shouldNotLeakForeignImage() {
        ProductImage saved =
                productImageRepository.saveAndFlush(image(product, "products/1/a.png", true));
        entityManager.clear();

        assertThat(productImageRepository.findByIdAndProductId(saved.getId(), otherProduct.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("findByIdAndProductId returns empty for an unknown image id")
    void shouldReturnEmptyForUnknownImageId() {
        assertThat(productImageRepository.findByIdAndProductId(9_999L, product.getId())).isEmpty();
    }

    @Test
    @DisplayName("restPrimaryImages demotes every image of the product")
    void shouldResetEveryPrimaryImageOfTheProduct() {
        ProductImage first =
                productImageRepository.saveAndFlush(image(product, "products/1/a.png", true));
        ProductImage second =
                productImageRepository.saveAndFlush(image(product, "products/1/b.png", true));
        entityManager.flush();

        productImageRepository.restPrimaryImages(product.getId());

        // the bulk update bypasses the persistence context, so it has to be cleared
        entityManager.flush();
        entityManager.clear();

        assertThat(productImageRepository.findById(first.getId()).orElseThrow().getIsPrimary())
                .isFalse();
        assertThat(productImageRepository.findById(second.getId()).orElseThrow().getIsPrimary())
                .isFalse();
    }

    @Test
    @DisplayName("restPrimaryImages never touches the images of another product")
    void shouldNotResetTheImagesOfAnotherProduct() {
        ProductImage mine =
                productImageRepository.saveAndFlush(image(product, "products/1/a.png", true));
        ProductImage foreign =
                productImageRepository.saveAndFlush(image(otherProduct, "products/2/a.png", true));
        entityManager.flush();

        productImageRepository.restPrimaryImages(product.getId());

        entityManager.flush();
        entityManager.clear();

        assertThat(productImageRepository.findById(mine.getId()).orElseThrow().getIsPrimary())
                .isFalse();
        assertThat(productImageRepository.findById(foreign.getId()).orElseThrow().getIsPrimary())
                .isTrue();
    }

    @Test
    @DisplayName("restPrimaryImages is a no-op for a product without images")
    void shouldBeANoOpForProductWithoutImages() {
        productImageRepository.restPrimaryImages(otherProduct.getId());

        entityManager.flush();
        entityManager.clear();

        assertThat(productImageRepository.count()).isZero();
    }

    @Test
    @DisplayName("swaps the stored key in place, as the update flow does")
    void shouldUpdateTheStoredKey() {
        ProductImage saved =
                productImageRepository.saveAndFlush(image(product, "products/1/old.png", false));

        saved.setUrl("products/1/new.png");
        saved.setIsPrimary(true);
        productImageRepository.saveAndFlush(saved);
        entityManager.clear();

        ProductImage reloaded = productImageRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getUrl()).isEqualTo("products/1/new.png");
        assertThat(reloaded.getIsPrimary()).isTrue();
    }

    @Test
    @DisplayName("deletes a single image without touching the product")
    void shouldDeleteImage() {
        ProductImage saved =
                productImageRepository.saveAndFlush(image(product, "products/1/a.png", true));

        productImageRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(productImageRepository.findById(saved.getId())).isEmpty();
        assertThat(productRepository.findById(product.getId())).isPresent();
    }

    @Test
    @DisplayName("accepts several images per product")
    void shouldAcceptSeveralImagesPerProduct() {
        productImageRepository.saveAndFlush(image(product, "products/1/a.png", true));
        productImageRepository.saveAndFlush(image(product, "products/1/b.png", false));
        productImageRepository.saveAndFlush(image(product, "products/1/c.png", false));
        entityManager.clear();

        assertThat(productImageRepository.count()).isEqualTo(3);
    }
}


package com.strikerkk.aicommerce.product_service.repository;

import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
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
@DisplayName("ProductVariantRepository")
class ProductVariantRepositoryTest {

    @Autowired
    private ProductVariantRepository productVariantRepository;

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

    private ProductVariant variant(Product parent, String size, int stockCount) {
        return ProductVariant.builder()
                .product(parent)
                .size(size)
                .color("Black")
                .stockCount(stockCount)
                .priceOverride(new BigDecimal("9499.00"))
                .build();
    }

    @Test
    @DisplayName("persists a variant and generates its id")
    void shouldPersistVariant() {
        ProductVariant saved = productVariantRepository.saveAndFlush(variant(product, "M", 10));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProduct().getId()).isEqualTo(product.getId());
        assertThat(saved.getStockCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("rejects a variant that is not attached to a product")
    void shouldRejectVariantWithoutProduct() {
        ProductVariant orphan = variant(null, "M", 10);

        assertThatThrownBy(() -> productVariantRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a variant without a stock count")
    void shouldRejectVariantWithoutStockCount() {
        ProductVariant invalid = variant(product, "M", 0);
        invalid.setStockCount(null);

        assertThatThrownBy(() -> productVariantRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("accepts a variant without a size, a colour or a price override")
    void shouldAcceptOptionalColumns() {
        ProductVariant sparse = ProductVariant.builder()
                .product(product)
                .stockCount(0)
                .build();

        ProductVariant saved = productVariantRepository.saveAndFlush(sparse);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSize()).isNull();
        assertThat(saved.getColor()).isNull();
        assertThat(saved.getPriceOverride()).isNull();
    }

    @Test
    @DisplayName("findByIdAndProductId returns the variant when it belongs to the product")
    void shouldFindByIdAndProductId() {
        ProductVariant saved = productVariantRepository.saveAndFlush(variant(product, "M", 10));
        entityManager.clear();

        Optional<ProductVariant> found =
                productVariantRepository.findByIdAndProductId(saved.getId(), product.getId());

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getSize()).isEqualTo("M");
    }

    @Test
    @DisplayName("findByIdAndProductId returns empty when the variant belongs to another product")
    void shouldNotLeakForeignVariant() {
        ProductVariant saved = productVariantRepository.saveAndFlush(variant(product, "M", 10));
        entityManager.clear();

        Optional<ProductVariant> found =
                productVariantRepository.findByIdAndProductId(saved.getId(), otherProduct.getId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndProductId returns empty for an unknown variant id")
    void shouldReturnEmptyForUnknownVariantId() {
        assertThat(productVariantRepository.findByIdAndProductId(9_999L, product.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByProductId returns every variant of the product only")
    void shouldFindEveryVariantOfTheProduct() {
        productVariantRepository.saveAndFlush(variant(product, "S", 1));
        productVariantRepository.saveAndFlush(variant(product, "M", 2));
        productVariantRepository.saveAndFlush(variant(otherProduct, "L", 3));
        entityManager.clear();

        List<ProductVariant> variants = productVariantRepository.findByProductId(product.getId());

        assertThat(variants).hasSize(2)
                .extracting(ProductVariant::getSize)
                .containsExactlyInAnyOrder("S", "M");
    }

    @Test
    @DisplayName("findByProductId returns an empty list for a product without variants")
    void shouldReturnEmptyListForProductWithoutVariants() {
        assertThat(productVariantRepository.findByProductId(otherProduct.getId())).isEmpty();
    }

    @Test
    @DisplayName("findByProductId returns an empty list for an unknown product")
    void shouldReturnEmptyListForUnknownProduct() {
        assertThat(productVariantRepository.findByProductId(9_999L)).isEmpty();
    }

    @Test
    @DisplayName("updates the stock count in place, as the order-confirmed consumer does")
    void shouldUpdateStockCount() {
        ProductVariant saved = productVariantRepository.saveAndFlush(variant(product, "M", 10));

        saved.setStockCount(saved.getStockCount() - 3);
        productVariantRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(productVariantRepository.findById(saved.getId()).orElseThrow().getStockCount())
                .isEqualTo(7);
    }

    @Test
    @DisplayName("saveAll writes every variant in one batch")
    void shouldSaveAll() {
        List<ProductVariant> batch =
                List.of(variant(product, "S", 1), variant(product, "M", 2), variant(product, "L", 3));

        productVariantRepository.saveAll(batch);
        entityManager.flush();
        entityManager.clear();

        assertThat(productVariantRepository.findByProductId(product.getId())).hasSize(3);
    }

    @Test
    @DisplayName("deletes a single variant without touching the product")
    void shouldDeleteVariant() {
        ProductVariant saved = productVariantRepository.saveAndFlush(variant(product, "M", 10));

        productVariantRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(productVariantRepository.findById(saved.getId())).isEmpty();
        assertThat(productRepository.findById(product.getId())).isPresent();
    }
}


package com.strikerkk.aicommerce.product_service.repository;

import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("ProductRepository")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Product product(String name, String createdBy) {
        return Product.builder()
                .name(name)
                .brand("JBL")
                .description("Portable speaker")
                .price(new BigDecimal("8999.00"))
                .category("Speakers")
                .stockCount(25)
                .isAvailable(true)
                .createdBy(createdBy)
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("persists a product and generates its id")
    void shouldPersistProduct() {
        Product saved = productRepository.saveAndFlush(product("JBL Flip 6", "admin-1"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("JBL Flip 6");
        assertThat(saved.getCreatedBy()).isEqualTo("admin-1");
    }

    @Test
    @DisplayName("stamps createdAt through the @PrePersist callback")
    void shouldStampCreatedAt() {
        Product saved = productRepository.saveAndFlush(product("JBL Flip 6", "admin-1"));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("keeps the decimal scale of the price")
    void shouldKeepThePriceScale() {
        Product saved = productRepository.saveAndFlush(product("JBL Flip 6", "admin-1"));
        entityManager.flush();
        entityManager.clear();

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPrice()).isEqualByComparingTo("8999.00");
    }

    @Test
    @DisplayName("rejects a product without a name")
    void shouldRejectMissingName() {
        Product invalid = product(null, "admin-1");

        assertThatThrownBy(() -> productRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a product without a brand")
    void shouldRejectMissingBrand() {
        Product invalid = product("JBL Flip 6", "admin-1");
        invalid.setBrand(null);

        assertThatThrownBy(() -> productRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a product without an owner")
    void shouldRejectMissingCreatedBy() {
        Product invalid = product("JBL Flip 6", null);

        assertThatThrownBy(() -> productRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a product without a stock count")
    void shouldRejectMissingStockCount() {
        Product invalid = product("JBL Flip 6", "admin-1");
        invalid.setStockCount(null);

        assertThatThrownBy(() -> productRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rejects a product without the availability flag")
    void shouldRejectMissingIsAvailable() {
        Product invalid = product("JBL Flip 6", "admin-1");
        invalid.setIsAvailable(null);

        assertThatThrownBy(() -> productRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("cascades the persist of the variants and of the images")
    void shouldCascadePersist() {
        Product product = product("JBL Flip 6", "admin-1");
        product.getVariants().add(ProductVariant.builder()
                .product(product).size("M").color("Black").stockCount(4)
                .priceOverride(new BigDecimal("9499.00")).build());
        product.getImages().add(ProductImage.builder()
                .product(product).url("products/x/key.png").isPrimary(true).build());

        Product saved = productRepository.saveAndFlush(product);
        entityManager.clear();

        assertThat(productVariantRepository.findByProductId(saved.getId())).hasSize(1);
        assertThat(productImageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("cascades the delete of the variants and of the images")
    void shouldCascadeDelete() {
        Product product = product("JBL Flip 6", "admin-1");
        product.getVariants().add(ProductVariant.builder()
                .product(product).stockCount(4).build());
        product.getImages().add(ProductImage.builder()
                .product(product).url("products/x/key.png").isPrimary(true).build());

        Product saved = productRepository.saveAndFlush(product);
        entityManager.flush();

        productRepository.delete(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(productRepository.findById(saved.getId())).isEmpty();
        assertThat(productVariantRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
    }

    @Test
    @DisplayName("removes an orphan variant when it leaves the collection")
    void shouldRemoveOrphanVariant() {
        Product product = product("JBL Flip 6", "admin-1");
        product.getVariants().add(ProductVariant.builder().product(product).stockCount(4).build());

        Product saved = productRepository.saveAndFlush(product);
        entityManager.flush();

        saved.getVariants().clear();
        productRepository.saveAndFlush(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(productVariantRepository.count()).isZero();
    }

    @Test
    @DisplayName("paginates and sorts the catalogue")
    void shouldPaginateAndSort() {
        productRepository.saveAndFlush(product("A-product", "admin-1"));
        productRepository.saveAndFlush(product("B-product", "admin-1"));
        productRepository.saveAndFlush(product("C-product", "admin-2"));
        entityManager.flush();

        Page<Product> firstPage =
                productRepository.findAll(PageRequest.of(0, 2, Sort.by("name")));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getNumber()).isZero();
        assertThat(firstPage.isLast()).isFalse();
        assertThat(firstPage.getContent())
                .extracting(Product::getName)
                .containsExactly("A-product", "B-product");

        Page<Product> lastPage =
                productRepository.findAll(PageRequest.of(1, 2, Sort.by("name")));

        assertThat(lastPage.isLast()).isTrue();
        assertThat(lastPage.getContent())
                .extracting(Product::getName)
                .containsExactly("C-product");
    }

    @Test
    @DisplayName("returns an empty page when the catalogue is empty")
    void shouldReturnEmptyPage() {
        Page<Product> page = productRepository.findAll(PageRequest.of(0, 10, Sort.by("id")));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.isLast()).isTrue();
    }

    @Test
    @DisplayName("updates a managed product in place")
    void shouldUpdateProduct() {
        Product saved = productRepository.saveAndFlush(product("JBL Flip 6", "admin-1"));

        saved.setName("JBL Charge 5");
        saved.setStockCount(0);
        saved.setIsAvailable(false);
        productRepository.saveAndFlush(saved);
        entityManager.clear();

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("JBL Charge 5");
        assertThat(reloaded.getStockCount()).isZero();
        assertThat(reloaded.getIsAvailable()).isFalse();
    }

    @Test
    @DisplayName("returns an empty optional for an unknown id")
    void shouldReturnEmptyOptionalForUnknownId() {
        Optional<Product> product = productRepository.findById(9_999L);

        assertThat(product).isEmpty();
    }

    @Test
    @DisplayName("stores a long description in the TEXT column")
    void shouldStoreALongDescription() {
        String longDescription = "x".repeat(5_000);
        Product product = product("JBL Flip 6", "admin-1");
        product.setDescription(longDescription);

        Product saved = productRepository.saveAndFlush(product);
        entityManager.clear();

        assertThat(productRepository.findById(saved.getId()).orElseThrow().getDescription())
                .hasSize(5_000);
    }
}


package com.strikerkk.aicommerce.product_service.config;

import com.strikerkk.aicommerce.product_service.dto.response.ProductImageResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductVariantResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiConfig / ModelMapper")
class ApiConfigTest {

    private ModelMapper modelMapper;

    @BeforeEach
    void setUp() {
        modelMapper = new ApiConfig().modelMapper();
    }

    private Product product() {
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
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("the configuration exposes a ModelMapper")
    void exposesAModelMapper() {
        assertThat(modelMapper).isNotNull();
    }

    // Product -> ProductResponse -------------------------------------------------------

    @Nested
    @DisplayName("Product -> ProductResponse")
    class ProductMapping {

        @Test
        @DisplayName("copies every scalar field")
        void copiesEveryScalarField() {
            ProductResponse response = modelMapper.map(product(), ProductResponse.class);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("JBL Flip 6");
            assertThat(response.getBrand()).isEqualTo("JBL");
            assertThat(response.getDescription()).isEqualTo("Portable speaker");
            assertThat(response.getPrice()).isEqualByComparingTo("8999.00");
            assertThat(response.getCategory()).isEqualTo("Speakers");
            assertThat(response.getStockCount()).isEqualTo(25);
            assertThat(response.getIsAvailable()).isTrue();
            assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        }

        @Test
        @DisplayName("never leaks the owner of the product - ProductResponse has no createdBy")
        void neverLeaksTheOwner() {
            assertThat(ProductResponse.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("createdBy");
        }

        @Test
        @DisplayName("maps the nested variants")
        void mapsTheNestedVariants() {
            Product product = product();
            product.getVariants().add(ProductVariant.builder()
                    .id(100L).product(product).size("M").color("Black")
                    .stockCount(4).priceOverride(new BigDecimal("9499.00")).build());

            ProductResponse response = modelMapper.map(product, ProductResponse.class);

            assertThat(response.getVariants()).hasSize(1);
            ProductVariantResponse variant = response.getVariants().getFirst();
            assertThat(variant.getId()).isEqualTo(100L);
            assertThat(variant.getSize()).isEqualTo("M");
            assertThat(variant.getColor()).isEqualTo("Black");
            assertThat(variant.getStockCount()).isEqualTo(4);
            assertThat(variant.getPriceOverride()).isEqualByComparingTo("9499.00");
        }

        @Test
        @DisplayName("maps the nested images")
        void mapsTheNestedImages() {
            Product product = product();
            product.getImages().add(ProductImage.builder()
                    .id(200L).product(product).url("products/1/a.png").isPrimary(true).build());

            ProductResponse response = modelMapper.map(product, ProductResponse.class);

            assertThat(response.getImages()).hasSize(1);
            assertThat(response.getImages().getFirst().getId()).isEqualTo(200L);
            assertThat(response.getImages().getFirst().getIsPrimary()).isTrue();
        }

        @Test
        @DisplayName("keeps the empty collections empty")
        void keepsTheEmptyCollectionsEmpty() {
            ProductResponse response = modelMapper.map(product(), ProductResponse.class);

            assertThat(response.getVariants()).isNotNull().isEmpty();
            assertThat(response.getImages()).isNotNull().isEmpty();
        }
    }

    // ProductVariant -> ProductVariantResponse -----------------------------------------

    @Nested
    @DisplayName("ProductVariant -> ProductVariantResponse")
    class VariantMapping {

        @Test
        @DisplayName("copies every field and never leaks the parent product")
        void copiesEveryField() {
            ProductVariant variant = ProductVariant.builder()
                    .id(100L)
                    .product(product())
                    .size("XL")
                    .color("Blue")
                    .stockCount(7)
                    .priceOverride(new BigDecimal("1234.50"))
                    .build();

            ProductVariantResponse response = modelMapper.map(variant, ProductVariantResponse.class);

            assertThat(response.getId()).isEqualTo(100L);
            assertThat(response.getSize()).isEqualTo("XL");
            assertThat(response.getColor()).isEqualTo("Blue");
            assertThat(response.getStockCount()).isEqualTo(7);
            assertThat(response.getPriceOverride()).isEqualByComparingTo("1234.50");
        }

        @Test
        @DisplayName("keeps the optional fields null")
        void keepsTheOptionalFieldsNull() {
            ProductVariant variant = ProductVariant.builder()
                    .id(101L).product(product()).stockCount(0).build();

            ProductVariantResponse response = modelMapper.map(variant, ProductVariantResponse.class);

            assertThat(response.getSize()).isNull();
            assertThat(response.getColor()).isNull();
            assertThat(response.getPriceOverride()).isNull();
            assertThat(response.getStockCount()).isZero();
        }

        @Test
        @DisplayName("takes the id of the variant, not the id of its product")
        void takesTheIdOfTheVariant() {
            Product parent = product();
            parent.setId(1L);
            ProductVariant variant = ProductVariant.builder()
                    .id(100L).product(parent).stockCount(1).build();

            assertThat(modelMapper.map(variant, ProductVariantResponse.class).getId())
                    .isEqualTo(100L);
        }
    }

    // ProductImage -> ProductImageResponse ----------------------------------------------

    @Nested
    @DisplayName("ProductImage -> ProductImageResponse")
    class ImageMapping {

        private ProductImage image() {
            Product parent = product();
            return ProductImage.builder()
                    .id(200L)
                    .product(parent)
                    .url("products/1/8f1b2c3d-flip6.png")
                    .isPrimary(true)
                    .build();
        }

        @Test
        @DisplayName("copies the id and the primary flag")
        void copiesTheIdAndThePrimaryFlag() {
            ProductImageResponse response = modelMapper.map(image(), ProductImageResponse.class);

            assertThat(response.getId()).isEqualTo(200L);
            assertThat(response.getIsPrimary()).isTrue();
        }

        @Test
        @DisplayName("keeps the primary flag false when the image is secondary")
        void keepsTheFlagFalse() {
            ProductImage image = image();
            image.setIsPrimary(false);

            assertThat(modelMapper.map(image, ProductImageResponse.class).getIsPrimary()).isFalse();
        }

        @Test
        @DisplayName("copies ProductImage.url into ProductImageResponse.imageUrl")
        void copiesTheUrlIntoImageUrl() {
            ProductImageResponse response = modelMapper.map(image(), ProductImageResponse.class);
            assertThat(response.getImageUrl()).isEqualTo("products/1/8f1b2c3d-flip6.png");
        }

        @Test
        @DisplayName("keeps the S3 key untouched - it is a key, never a fully qualified URL")
        void keepsTheKeyUntouched() {
            ProductImage image = image();
            image.setUrl("products/42/some-uuid-name.png");

            assertThat(modelMapper.map(image, ProductImageResponse.class).getImageUrl())
                    .isEqualTo("products/42/some-uuid-name.png")
                    .doesNotStartWith("http");
        }
    }
}



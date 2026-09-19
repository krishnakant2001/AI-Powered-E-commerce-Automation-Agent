package com.strikerkk.aicommerce.product_service.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Request DTO validation")
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static <T> Set<String> messagesFor(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(java.util.stream.Collectors.toSet());
    }

    // ProductRequest --------------------------------------------------------------

    @Nested
    @DisplayName("ProductRequest")
    class ProductRequestValidation {

        private ProductRequest valid() {
            ProductRequest request = new ProductRequest();
            request.setName("JBL Flip 6");
            request.setBrand("JBL");
            request.setDescription("Portable speaker");
            request.setPrice(new BigDecimal("8999.00"));
            request.setCategory("Speakers");
            request.setStockCount(25);
            return request;
        }

        @Test
        @DisplayName("accepts a complete payload")
        void acceptsACompletePayload() {
            assertThat(validator.validate(valid())).isEmpty();
        }

        @Test
        @DisplayName("isAvailable defaults to true")
        void isAvailableDefaultsToTrue() {
            assertThat(new ProductRequest().getIsAvailable()).isTrue();
        }

        @Test
        @DisplayName("the description is optional")
        void descriptionIsOptional() {
            ProductRequest request = valid();
            request.setDescription(null);

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a null name")
        void rejectsANullName() {
            ProductRequest request = valid();
            request.setName(null);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("name: Product name is required");
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsABlankName() {
            ProductRequest request = valid();
            request.setName("   ");

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("name: Product name is required");
        }

        @Test
        @DisplayName("rejects a blank brand")
        void rejectsABlankBrand() {
            ProductRequest request = valid();
            request.setBrand("");

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("brand: Brand is required");
        }

        @Test
        @DisplayName("rejects a blank category")
        void rejectsABlankCategory() {
            ProductRequest request = valid();
            request.setCategory("  ");

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("category: Category is required");
        }

        @Test
        @DisplayName("rejects a null price")
        void rejectsANullPrice() {
            ProductRequest request = valid();
            request.setPrice(null);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("price: Price is required");
        }

        @Test
        @DisplayName("rejects a price of exactly zero - the bound is exclusive")
        void rejectsAZeroPrice() {
            ProductRequest request = valid();
            request.setPrice(BigDecimal.ZERO);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("price: Price must be greater than 0");
        }

        @Test
        @DisplayName("rejects a negative price")
        void rejectsANegativePrice() {
            ProductRequest request = valid();
            request.setPrice(new BigDecimal("-1.00"));

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("price: Price must be greater than 0");
        }

        @Test
        @DisplayName("accepts the smallest positive price")
        void acceptsTheSmallestPositivePrice() {
            ProductRequest request = valid();
            request.setPrice(new BigDecimal("0.01"));

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a null stock count")
        void rejectsANullStockCount() {
            ProductRequest request = valid();
            request.setStockCount(null);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("stockCount: Stock count is required");
        }

        @Test
        @DisplayName("rejects a negative stock count")
        void rejectsANegativeStockCount() {
            ProductRequest request = valid();
            request.setStockCount(-1);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("stockCount: Stock count cannot be negative");
        }

        @Test
        @DisplayName("accepts a stock count of zero - the product is simply sold out")
        void acceptsAZeroStockCount() {
            ProductRequest request = valid();
            request.setStockCount(0);

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("reports every violation of an empty payload")
        void reportsEveryViolationOfAnEmptyPayload() {
            assertThat(messagesFor(validator.validate(new ProductRequest())))
                    .containsExactlyInAnyOrder(
                            "name: Product name is required",
                            "brand: Brand is required",
                            "category: Category is required",
                            "price: Price is required",
                            "stockCount: Stock count is required");
        }
    }

    // ProductVariantRequest -------------------------------------------------------

    @Nested
    @DisplayName("ProductVariantRequest")
    class ProductVariantRequestValidation {

        private ProductVariantRequest valid() {
            ProductVariantRequest request = new ProductVariantRequest();
            request.setSize("M");
            request.setColor("Black");
            request.setStockCount(10);
            request.setPriceOverride(new BigDecimal("9499.00"));
            return request;
        }

        @Test
        @DisplayName("accepts a complete payload")
        void acceptsACompletePayload() {
            assertThat(validator.validate(valid())).isEmpty();
        }

        @Test
        @DisplayName("the size, the colour and the price override are optional")
        void optionalFieldsMayStayNull() {
            ProductVariantRequest request = new ProductVariantRequest();
            request.setStockCount(0);

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a null stock count")
        void rejectsANullStockCount() {
            ProductVariantRequest request = valid();
            request.setStockCount(null);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("stockCount: Stock count is required");
        }

        @Test
        @DisplayName("rejects a negative stock count")
        void rejectsANegativeStockCount() {
            ProductVariantRequest request = valid();
            request.setStockCount(-3);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("stockCount: Stock count cannot be negative");
        }

        @Test
        @DisplayName("rejects a price override of exactly zero")
        void rejectsAZeroPriceOverride() {
            ProductVariantRequest request = valid();
            request.setPriceOverride(BigDecimal.ZERO);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("priceOverride: Price override must be greater than 0");
        }

        @Test
        @DisplayName("rejects a negative price override")
        void rejectsANegativePriceOverride() {
            ProductVariantRequest request = valid();
            request.setPriceOverride(new BigDecimal("-0.01"));

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("priceOverride: Price override must be greater than 0");
        }

        @Test
        @DisplayName("reports every violation of an empty payload")
        void reportsEveryViolationOfAnEmptyPayload() {
            assertThat(messagesFor(validator.validate(new ProductVariantRequest())))
                    .containsExactly("stockCount: Stock count is required");
        }
    }

    // ProductImageRequest ---------------------------------------------------------

    @Nested
    @DisplayName("ProductImageRequest")
    class ProductImageRequestValidation {

        @Test
        @DisplayName("accepts a payload that carries a file")
        void acceptsAPayloadWithAFile() {
            ProductImageRequest request = new ProductImageRequest(
                    new MockMultipartFile("image", "a.png", "image/png", "bytes".getBytes()), true);

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a payload without a file")
        void rejectsAPayloadWithoutAFile() {
            ProductImageRequest request = new ProductImageRequest(null, true);

            assertThat(messagesFor(validator.validate(request)))
                    .containsExactly("image: Image file is required");
        }

        @Test
        @DisplayName("isPrimary defaults to false")
        void isPrimaryDefaultsToFalse() {
            assertThat(new ProductImageRequest().getIsPrimary()).isFalse();
        }

        @Test
        @DisplayName("the all-args constructor keeps the supplied flag, including null")
        void allArgsConstructorKeepsTheSuppliedFlag() {
            assertThat(new ProductImageRequest(null, null).getIsPrimary()).isNull();
            assertThat(new ProductImageRequest(null, true).getIsPrimary()).isTrue();
            assertThat(new ProductImageRequest(null, false).getIsPrimary()).isFalse();
        }

        @Test
        @DisplayName("an empty file still satisfies @NotNull - the emptiness is handled by the service")
        void anEmptyFileIsStillNotNull() {
            ProductImageRequest request = new ProductImageRequest(
                    new MockMultipartFile("image", "a.png", "image/png", new byte[0]), false);

            assertThat(validator.validate(request)).isEmpty();
        }
    }
}


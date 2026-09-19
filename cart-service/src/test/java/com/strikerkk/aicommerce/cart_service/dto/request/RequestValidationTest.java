package com.strikerkk.aicommerce.cart_service.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    private <T> Set<ConstraintViolation<T>> violations(T bean) {
        return validator.validate(bean);
    }

    private AddCartItemRequest addRequest(Long productId, Long variantId, Integer quantity) {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(productId);
        request.setVariantId(variantId);
        request.setQuantity(quantity);
        return request;
    }

    private UpdateCartItemRequest updateRequest(Integer quantity) {
        UpdateCartItemRequest request = new UpdateCartItemRequest();
        request.setQuantity(quantity);
        return request;
    }

    @Nested
    @DisplayName("AddCartItemRequest")
    class Add {

        @Test
        @DisplayName("a fully populated request is valid")
        void aValidRequestPasses() {
            assertThat(violations(addRequest(1L, 2L, 1))).isEmpty();
        }

        @Test
        @DisplayName("productId is required")
        void productIdIsRequired() {
            assertThat(violations(addRequest(null, 2L, 1)))
                    .singleElement()
                    .satisfies(v -> {
                        assertThat(v.getPropertyPath()).hasToString("productId");
                        assertThat(v.getMessage()).isEqualTo("Product ID is required");
                    });
        }

        @Test
        @DisplayName("variantId is required")
        void variantIdIsRequired() {
            assertThat(violations(addRequest(1L, null, 1)))
                    .singleElement()
                    .satisfies(v -> {
                        assertThat(v.getPropertyPath()).hasToString("variantId");
                        assertThat(v.getMessage()).isEqualTo("Variant ID is required");
                    });
        }

        @Test
        @DisplayName("quantity is required")
        void quantityIsRequired() {
            assertThat(violations(addRequest(1L, 2L, null)))
                    .singleElement()
                    .satisfies(v -> {
                        assertThat(v.getPropertyPath()).hasToString("quantity");
                        assertThat(v.getMessage()).isEqualTo("Quantity is required");
                    });
        }

        @Test
        @DisplayName("quantity must be at least 1")
        void quantityMustBeAtLeastOne() {
            assertThat(violations(addRequest(1L, 2L, 0)))
                    .singleElement()
                    .satisfies(v -> {
                        assertThat(v.getPropertyPath()).hasToString("quantity");
                        assertThat(v.getMessage()).isEqualTo("Quantity must be at least 1");
                    });
        }

        @Test
        @DisplayName("a negative quantity is rejected")
        void rejectsANegativeQuantity() {
            assertThat(violations(addRequest(1L, 2L, -5))).hasSize(1);
        }

        @Test
        @DisplayName("a large quantity is accepted - there is no upper bound today")
        void hasNoUpperBound() {
            assertThat(violations(addRequest(1L, 2L, Integer.MAX_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("an empty request reports all three missing fields")
        void reportsEveryMissingField() {
            assertThat(violations(new AddCartItemRequest()))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("productId", "variantId", "quantity");
        }

        /** Price tampering is impossible: the DTO simply has no price field. */
        @Test
        @DisplayName("carries no price - the price is always resolved server side")
        void carriesNoPrice() {
            assertThat(AddCartItemRequest.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactlyInAnyOrder("productId", "variantId", "quantity");
        }

        @Test
        @DisplayName("equals / hashCode / toString come from @Data")
        void valueSemantics() {
            assertThat(addRequest(1L, 2L, 3))
                    .isEqualTo(addRequest(1L, 2L, 3))
                    .hasSameHashCodeAs(addRequest(1L, 2L, 3));
            assertThat(addRequest(1L, 2L, 3)).isNotEqualTo(addRequest(1L, 2L, 4));
            assertThat(addRequest(1L, 2L, 3).toString()).contains("productId=1", "variantId=2", "quantity=3");
        }
    }

    @Nested
    @DisplayName("UpdateCartItemRequest")
    class Update {

        @Test
        @DisplayName("a quantity of 1 is valid")
        void aValidRequestPasses() {
            assertThat(violations(updateRequest(1))).isEmpty();
        }

        @Test
        @DisplayName("quantity is required")
        void quantityIsRequired() {
            assertThat(violations(updateRequest(null)))
                    .singleElement()
                    .satisfies(v -> {
                        assertThat(v.getPropertyPath()).hasToString("quantity");
                        assertThat(v.getMessage()).isEqualTo("Quantity is required");
                    });
        }

        @Test
        @DisplayName("quantity must be at least 1 - a removal goes through DELETE, not through a 0")
        void quantityMustBeAtLeastOne() {
            assertThat(violations(updateRequest(0)))
                    .singleElement()
                    .satisfies(v -> assertThat(v.getMessage()).isEqualTo("Quantity must be at least 1"));
        }

        @Test
        @DisplayName("a negative quantity is rejected")
        void rejectsANegativeQuantity() {
            assertThat(violations(updateRequest(-1))).hasSize(1);
        }

        /** productId / variantId are immutable: changing them means delete + add. */
        @Test
        @DisplayName("only the quantity is updatable")
        void onlyTheQuantityIsUpdatable() {
            assertThat(UpdateCartItemRequest.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactly("quantity");
        }

        @Test
        @DisplayName("equals / hashCode / toString come from @Data")
        void valueSemantics() {
            assertThat(updateRequest(3)).isEqualTo(updateRequest(3)).hasSameHashCodeAs(updateRequest(3));
            assertThat(updateRequest(3)).isNotEqualTo(updateRequest(4));
            assertThat(updateRequest(3).toString()).contains("quantity=3");
        }
    }
}


package com.strikerkk.aicommerce.product_service.helper;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductOwnershipValidator")
class ProductOwnershipValidatorTest {

    private final ProductOwnershipValidator validator = new ProductOwnershipValidator();

    @BeforeEach
    @AfterEach
    void reset() {
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    private Product productOwnedBy(String createdBy) {
        return Product.builder().id(1L).name("JBL Flip 6").createdBy(createdBy).build();
    }

    @Test
    @DisplayName("accepts the admin that created the product")
    void shouldAcceptTheOwner() {
        UserContext.setUserId("admin-1");

        assertThatCode(() -> validator.validate(productOwnedBy("admin-1")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects another admin")
    void shouldRejectAnotherAdmin() {
        UserContext.setUserId("admin-2");

        assertThatThrownBy(() -> validator.validate(productOwnedBy("admin-1")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You are not authorised to modify this product");
    }

    @Test
    @DisplayName("rejects an anonymous caller")
    void shouldRejectAnAnonymousCaller() {
        assertThatThrownBy(() -> validator.validate(productOwnedBy("admin-1")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You are not authorised to modify this product");
    }

    @Test
    @DisplayName("the comparison is case sensitive")
    void theComparisonIsCaseSensitive() {
        UserContext.setUserId("ADMIN-1");

        assertThatThrownBy(() -> validator.validate(productOwnedBy("admin-1")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("an id that only differs by whitespace is rejected")
    void whitespaceIsSignificant() {
        UserContext.setUserId(" admin-1");

        assertThatThrownBy(() -> validator.validate(productOwnedBy("admin-1")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("the role never grants access - only the ownership does")
    void theRoleDoesNotGrantAccess() {
        UserContext.setUserId("admin-2");
        UserContext.setUserRole("ADMIN");

        assertThatThrownBy(() -> validator.validate(productOwnedBy("admin-1")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("a product without an owner blows up - createdBy is nullable=false in the schema")
    void aProductWithoutAnOwnerBlowsUp() {
        UserContext.setUserId("admin-1");

        assertThatThrownBy(() -> validator.validate(productOwnedBy(null)))
                .isInstanceOf(NullPointerException.class);
    }
}


package com.strikerkk.aicommerce.product_service.controller;

import com.strikerkk.aicommerce.product_service.service.ProductImageService;
import com.strikerkk.aicommerce.product_service.service.ProductService;
import com.strikerkk.aicommerce.product_service.service.ProductVariantService;
import com.strikerkk.aicommerce.product_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@DisplayName("ProductAdminController method security")
class ProductAdminControllerMethodSecurityTest {

    @Configuration
    @EnableMethodSecurity(securedEnabled = true)
    static class TestConfig {

        @Bean
        ProductService productService() {
            return Mockito.mock(ProductService.class);
        }

        @Bean
        ProductVariantService productVariantService() {
            return Mockito.mock(ProductVariantService.class);
        }

        @Bean
        ProductImageService productImageService() {
            return Mockito.mock(ProductImageService.class);
        }

        @Bean
        ProductAdminController productAdminController(ProductService productService,
                                                      ProductVariantService productVariantService,
                                                      ProductImageService productImageService) {
            return new ProductAdminController(productService, productVariantService, productImageService);
        }
    }

    @Autowired
    private ProductAdminController controller;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductVariantService productVariantService;

    @Autowired
    private ProductImageService productImageService;

    @BeforeEach
    void setUp() {
        Mockito.reset(productService, productVariantService, productImageService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin-1", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    // products ------------------------------------------------------------------------

    @Test
    @DisplayName("an ADMIN can create a product")
    void adminCanCreateProduct() {
        authenticateWithRole("ADMIN");
        when(productService.createProduct(any())).thenReturn(TestDataFactory.productResponse());

        assertThatCode(() -> controller.createProduct(TestDataFactory.productRequest()))
                .doesNotThrowAnyException();

        verify(productService).createProduct(any());
    }

    @Test
    @DisplayName("a USER cannot create a product")
    void userCannotCreateProduct() {
        authenticateWithRole("USER");

        assertThatThrownBy(() -> controller.createProduct(TestDataFactory.productRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("an anonymous caller cannot create a product")
    void anonymousCannotCreateProduct() {
        assertThatThrownBy(() -> controller.createProduct(TestDataFactory.productRequest()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("a USER cannot update a product")
    void userCannotUpdateProduct() {
        authenticateWithRole("USER");

        assertThatThrownBy(() ->
                controller.updateProduct(TestDataFactory.productRequest(), 1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(productService, never()).updateProduct(any(), anyLong());
    }

    @Test
    @DisplayName("a USER cannot delete a product")
    void userCannotDeleteProduct() {
        authenticateWithRole("USER");

        assertThatThrownBy(() -> controller.deleteProduct(1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(productService, never()).deleteProduct(anyLong());
    }

    @Test
    @DisplayName("an ADMIN can update and delete a product")
    void adminCanUpdateAndDeleteProduct() {
        authenticateWithRole("ADMIN");
        when(productService.updateProduct(any(), anyLong())).thenReturn(TestDataFactory.productResponse());

        assertThatCode(() -> {
            controller.updateProduct(TestDataFactory.productRequest(), 1L);
            controller.deleteProduct(1L);
        }).doesNotThrowAnyException();

        verify(productService).updateProduct(any(), anyLong());
        verify(productService).deleteProduct(1L);
    }

    // variants ------------------------------------------------------------------------

    @Test
    @DisplayName("a USER cannot touch the variants")
    void userCannotTouchTheVariants() {
        authenticateWithRole("USER");

        assertThatThrownBy(() ->
                controller.createProductVariant(TestDataFactory.productVariantRequest(), 1L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                controller.updateProductVariant(TestDataFactory.productVariantRequest(), 1L, 100L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.deleteProductVariant(1L, 100L))
                .isInstanceOf(AccessDeniedException.class);

        Mockito.verifyNoInteractions(productVariantService);
    }

    @Test
    @DisplayName("an ADMIN can manage the variants")
    void adminCanManageTheVariants() {
        authenticateWithRole("ADMIN");
        when(productVariantService.createProductVariant(any(), anyLong()))
                .thenReturn(TestDataFactory.productVariantResponse());
        when(productVariantService.updateProductVariant(any(), anyLong(), anyLong()))
                .thenReturn(TestDataFactory.productVariantResponse());

        assertThatCode(() -> {
            controller.createProductVariant(TestDataFactory.productVariantRequest(), 1L);
            controller.updateProductVariant(TestDataFactory.productVariantRequest(), 1L, 100L);
            controller.deleteProductVariant(1L, 100L);
        }).doesNotThrowAnyException();

        verify(productVariantService).deleteProductVariant(1L, 100L);
    }

    // images --------------------------------------------------------------------------

    @Test
    @DisplayName("a USER cannot touch the images")
    void userCannotTouchTheImages() {
        authenticateWithRole("USER");

        assertThatThrownBy(() ->
                controller.createProductImage(TestDataFactory.multipartFile(), "true", 1L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() ->
                controller.updateProductImage(TestDataFactory.multipartFile(), "true", 1L, 200L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.deleteProductImage(1L, 200L))
                .isInstanceOf(AccessDeniedException.class);

        Mockito.verifyNoInteractions(productImageService);
    }

    @Test
    @DisplayName("an ADMIN can manage the images")
    void adminCanManageTheImages() {
        authenticateWithRole("ADMIN");
        when(productImageService.addProductImage(any(), anyLong()))
                .thenReturn(TestDataFactory.productImageResponse(true));
        when(productImageService.updateProductImage(any(), anyLong(), anyLong()))
                .thenReturn(TestDataFactory.productImageResponse(true));

        assertThatCode(() -> {
            controller.createProductImage(TestDataFactory.multipartFile(), "true", 1L);
            controller.updateProductImage(TestDataFactory.multipartFile(), "false", 1L, 200L);
            controller.deleteProductImage(1L, 200L);
        }).doesNotThrowAnyException();

        verify(productImageService).deleteProductImage(1L, 200L);
    }

    @Test
    @DisplayName("an unrelated role is denied as well")
    void anUnrelatedRoleIsDenied() {
        authenticateWithRole("GUEST");

        assertThatThrownBy(() -> controller.deleteProduct(1L))
                .isInstanceOf(AccessDeniedException.class);
    }
}


package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.dto.request.ProductVariantRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductVariantResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductVariant;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.product_service.helper.ProductOwnershipValidator;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductVariantRepository;
import com.strikerkk.aicommerce.product_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductVariantService")
class ProductVariantServiceTest {

    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOwnershipValidator productOwnershipValidator;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private ProductVariantService productVariantService;

    @Captor
    private ArgumentCaptor<ProductVariant> variantCaptor;

    private Product product;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(TestDataFactory.ADMIN_ID);
        UserContext.setUserRole("ADMIN");
        product = TestDataFactory.product();
    }

    @AfterEach
    void tearDown() {
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    // createProductVariant -------------------------------------------------------------

    @Nested
    @DisplayName("createProductVariant")
    class CreateProductVariant {

        @Test
        @DisplayName("attaches the variant to its product and copies every field")
        void shouldCreateVariant() {
            ProductVariantRequest request = TestDataFactory.productVariantRequest();
            ProductVariant saved = TestDataFactory.variant(VARIANT_ID, product);
            ProductVariantResponse expected = TestDataFactory.productVariantResponse();

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.save(any(ProductVariant.class))).thenReturn(saved);
            when(modelMapper.map(saved, ProductVariantResponse.class)).thenReturn(expected);

            ProductVariantResponse actual = productVariantService.createProductVariant(request, PRODUCT_ID);

            assertThat(actual).isSameAs(expected);

            verify(productVariantRepository).save(variantCaptor.capture());
            ProductVariant persisted = variantCaptor.getValue();
            assertThat(persisted.getId()).isNull();
            assertThat(persisted.getProduct()).isSameAs(product);
            assertThat(persisted.getSize()).isEqualTo("M");
            assertThat(persisted.getColor()).isEqualTo("Black");
            assertThat(persisted.getStockCount()).isEqualTo(10);
            assertThat(persisted.getPriceOverride()).isEqualByComparingTo(TestDataFactory.PRICE_OVERRIDE);
        }

        @Test
        @DisplayName("accepts a variant without size, colour or price override")
        void shouldAcceptOptionalFields() {
            ProductVariantRequest request = new ProductVariantRequest();
            request.setStockCount(0);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.save(any(ProductVariant.class)))
                    .thenReturn(TestDataFactory.variant(VARIANT_ID, product));
            when(modelMapper.map(any(), eq(ProductVariantResponse.class)))
                    .thenReturn(TestDataFactory.productVariantResponse());

            productVariantService.createProductVariant(request, PRODUCT_ID);

            verify(productVariantRepository).save(variantCaptor.capture());
            assertThat(variantCaptor.getValue().getSize()).isNull();
            assertThat(variantCaptor.getValue().getColor()).isNull();
            assertThat(variantCaptor.getValue().getPriceOverride()).isNull();
            assertThat(variantCaptor.getValue().getStockCount()).isZero();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the product does not exist")
        void shouldThrowWhenProductMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    productVariantService.createProductVariant(TestDataFactory.productVariantRequest(), 404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product not found");

            verifyNoInteractions(productOwnershipValidator, productVariantRepository);
        }

        @Test
        @DisplayName("refuses to add a variant to a product owned by another admin")
        void shouldRejectForeignProduct() {
            Product foreign = TestDataFactory.product(PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreign));
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productOwnershipValidator).validate(foreign);

            assertThatThrownBy(() -> productVariantService
                    .createProductVariant(TestDataFactory.productVariantRequest(), PRODUCT_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verify(productVariantRepository, never()).save(any());
        }

        @Test
        @DisplayName("checks the ownership before touching the variant table")
        void shouldValidateBeforeSaving() {
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.save(any(ProductVariant.class)))
                    .thenReturn(TestDataFactory.variant(VARIANT_ID, product));
            when(modelMapper.map(any(), eq(ProductVariantResponse.class)))
                    .thenReturn(TestDataFactory.productVariantResponse());

            productVariantService.createProductVariant(TestDataFactory.productVariantRequest(), PRODUCT_ID);

            InOrder inOrder = Mockito.inOrder(productRepository, productOwnershipValidator, productVariantRepository);
            inOrder.verify(productRepository).findById(PRODUCT_ID);
            inOrder.verify(productOwnershipValidator).validate(product);
            inOrder.verify(productVariantRepository).save(any(ProductVariant.class));
        }
    }

    // updateProductVariant -------------------------------------------------------------

    @Nested
    @DisplayName("updateProductVariant")
    class UpdateProductVariant {

        @Test
        @DisplayName("overwrites every mutable field of the variant")
        void shouldUpdateVariant() {
            ProductVariant variant = TestDataFactory.variant(VARIANT_ID, product);
            ProductVariantRequest request = TestDataFactory.productVariantRequest();
            request.setSize("XL");
            request.setColor("Blue");
            request.setStockCount(77);
            request.setPriceOverride(new BigDecimal("1234.50"));

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(variant));
            when(productVariantRepository.save(variant)).thenReturn(variant);
            when(modelMapper.map(variant, ProductVariantResponse.class))
                    .thenReturn(TestDataFactory.productVariantResponse());

            productVariantService.updateProductVariant(request, PRODUCT_ID, VARIANT_ID);

            assertThat(variant.getSize()).isEqualTo("XL");
            assertThat(variant.getColor()).isEqualTo("Blue");
            assertThat(variant.getStockCount()).isEqualTo(77);
            assertThat(variant.getPriceOverride()).isEqualByComparingTo("1234.50");
            verify(productVariantRepository).save(variant);
        }

        @Test
        @DisplayName("never re-parents the variant")
        void shouldKeepTheParentProduct() {
            ProductVariant variant = TestDataFactory.variant(VARIANT_ID, product);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(variant));
            when(productVariantRepository.save(variant)).thenReturn(variant);
            when(modelMapper.map(variant, ProductVariantResponse.class))
                    .thenReturn(TestDataFactory.productVariantResponse());

            productVariantService.updateProductVariant(
                    TestDataFactory.productVariantRequest(), PRODUCT_ID, VARIANT_ID);

            assertThat(variant.getProduct()).isSameAs(product);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the product does not exist")
        void shouldThrowWhenProductMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productVariantService.updateProductVariant(
                    TestDataFactory.productVariantRequest(), 404L, VARIANT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product not found");

            verifyNoInteractions(productVariantRepository, productOwnershipValidator);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the variant belongs to another product")
        void shouldThrowWhenVariantDoesNotBelongToProduct() {
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.findByIdAndProductId(999L, PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productVariantService.updateProductVariant(
                    TestDataFactory.productVariantRequest(), PRODUCT_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Variant not found for this product");

            verify(productVariantRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses to update a variant of a product owned by another admin")
        void shouldRejectForeignProduct() {
            Product foreign = TestDataFactory.product(PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            ProductVariant variant = TestDataFactory.variant(VARIANT_ID, foreign);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreign));
            when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(variant));
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productOwnershipValidator).validate(foreign);

            assertThatThrownBy(() -> productVariantService.updateProductVariant(
                    TestDataFactory.productVariantRequest(), PRODUCT_ID, VARIANT_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verify(productVariantRepository, never()).save(any());
        }

        @Test
        @DisplayName("looks the variant up before it checks the ownership (current behaviour)")
        void shouldResolveVariantBeforeOwnershipCheck() {
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.findByIdAndProductId(999L, PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productVariantService.updateProductVariant(
                    TestDataFactory.productVariantRequest(), PRODUCT_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);

            // the ownership validator is only reached once the variant has been resolved
            verifyNoInteractions(productOwnershipValidator);
        }
    }

    // deleteProductVariant -------------------------------------------------------------

    @Nested
    @DisplayName("deleteProductVariant")
    class DeleteProductVariant {

        @Test
        @DisplayName("deletes the variant of the product")
        void shouldDeleteVariant() {
            ProductVariant variant = TestDataFactory.variant(VARIANT_ID, product);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(variant));

            productVariantService.deleteProductVariant(PRODUCT_ID, VARIANT_ID);

            verify(productOwnershipValidator).validate(product);
            verify(productVariantRepository).delete(variant);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the product does not exist")
        void shouldThrowWhenProductMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productVariantService.deleteProductVariant(404L, VARIANT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product not found");

            verifyNoInteractions(productVariantRepository, productOwnershipValidator);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the variant belongs to another product")
        void shouldThrowWhenVariantDoesNotBelongToProduct() {
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productVariantRepository.findByIdAndProductId(999L, PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productVariantService.deleteProductVariant(PRODUCT_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Variant not found for this product");

            verify(productVariantRepository, never()).delete(any());
        }

        @Test
        @DisplayName("checks the ownership before it even looks the variant up")
        void shouldValidateOwnershipFirst() {
            Product foreign = TestDataFactory.product(PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreign));
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productOwnershipValidator).validate(foreign);

            assertThatThrownBy(() -> productVariantService.deleteProductVariant(PRODUCT_ID, VARIANT_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verifyNoInteractions(productVariantRepository);
        }
    }
}


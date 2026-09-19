package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.common.PageResponse;
import com.strikerkk.aicommerce.product_service.dto.request.ProductRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.product_service.helper.ProductOwnershipValidator;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import com.strikerkk.aicommerce.product_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOwnershipValidator productOwnershipValidator;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private ProductService productService;

    @Captor
    private ArgumentCaptor<Product> productCaptor;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(TestDataFactory.ADMIN_ID);
        UserContext.setUserRole("ADMIN");
    }

    @AfterEach
    void tearDown() {
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    // createProduct ------------------------------------------------------------------

    @Nested
    @DisplayName("createProduct")
    class CreateProduct {

        @Test
        @DisplayName("persists every field of the request and returns the mapped response")
        void shouldCreateProduct() {
            ProductRequest request = TestDataFactory.productRequest();
            Product saved = TestDataFactory.product();
            ProductResponse expected = TestDataFactory.productResponse();

            when(productRepository.save(any(Product.class))).thenReturn(saved);
            when(modelMapper.map(saved, ProductResponse.class)).thenReturn(expected);

            ProductResponse actual = productService.createProduct(request);

            assertThat(actual).isSameAs(expected);

            verify(productRepository).save(productCaptor.capture());
            Product persisted = productCaptor.getValue();
            assertThat(persisted.getId()).isNull();
            assertThat(persisted.getName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(persisted.getBrand()).isEqualTo(TestDataFactory.BRAND);
            assertThat(persisted.getDescription()).isEqualTo("Portable waterproof bluetooth speaker");
            assertThat(persisted.getPrice()).isEqualByComparingTo(TestDataFactory.PRICE);
            assertThat(persisted.getCategory()).isEqualTo(TestDataFactory.CATEGORY);
            assertThat(persisted.getStockCount()).isEqualTo(25);
            assertThat(persisted.getIsAvailable()).isTrue();
        }

        @Test
        @DisplayName("stamps the caller resolved from UserContext as the owner")
        void shouldStampTheCallerAsOwner() {
            UserContext.setUserId("admin-99");

            when(productRepository.save(any(Product.class))).thenReturn(TestDataFactory.product());
            when(modelMapper.map(any(), eq(ProductResponse.class)))
                    .thenReturn(TestDataFactory.productResponse());

            productService.createProduct(TestDataFactory.productRequest());

            verify(productRepository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().getCreatedBy()).isEqualTo("admin-99");
        }

        @Test
        @DisplayName("initialises the variant and image collections so they are never null")
        void shouldInitialiseCollections() {
            when(productRepository.save(any(Product.class))).thenReturn(TestDataFactory.product());
            when(modelMapper.map(any(), eq(ProductResponse.class)))
                    .thenReturn(TestDataFactory.productResponse());

            productService.createProduct(TestDataFactory.productRequest());

            verify(productRepository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().getVariants()).isNotNull().isEmpty();
            assertThat(productCaptor.getValue().getImages()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("keeps the null flags of the request instead of inventing defaults")
        void shouldKeepNullFlags() {
            ProductRequest request = TestDataFactory.productRequest();
            request.setIsAvailable(null);
            request.setDescription(null);

            when(productRepository.save(any(Product.class))).thenReturn(TestDataFactory.product());
            when(modelMapper.map(any(), eq(ProductResponse.class)))
                    .thenReturn(TestDataFactory.productResponse());

            productService.createProduct(request);

            verify(productRepository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().getIsAvailable()).isNull();
            assertThat(productCaptor.getValue().getDescription()).isNull();
        }

        @Test
        @DisplayName("does not run the ownership check - a brand new product has no owner yet")
        void shouldNotValidateOwnership() {
            when(productRepository.save(any(Product.class))).thenReturn(TestDataFactory.product());
            when(modelMapper.map(any(), eq(ProductResponse.class)))
                    .thenReturn(TestDataFactory.productResponse());

            productService.createProduct(TestDataFactory.productRequest());

            verifyNoInteractions(productOwnershipValidator);
        }
    }

    // getAllProducts ------------------------------------------------------------------

    @Nested
    @DisplayName("getAllProducts")
    class GetAllProducts {

        @Test
        @DisplayName("copies every pagination attribute of the Spring Data page")
        void shouldMapPageMetadata() {
            Pageable pageable = PageRequest.of(1, 2, Sort.by("id"));
            List<Product> products = List.of(
                    TestDataFactory.product(3L, TestDataFactory.ADMIN_ID),
                    TestDataFactory.product(4L, TestDataFactory.ADMIN_ID));
            Page<Product> page = new PageImpl<>(products, pageable, 6);

            when(productRepository.findAll(pageable)).thenReturn(page);
            when(modelMapper.map(any(Product.class), eq(ProductResponse.class)))
                    .thenReturn(TestDataFactory.productResponse());

            PageResponse<ProductResponse> response = productService.getAllProducts(pageable);

            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getPageNumber()).isEqualTo(1);
            assertThat(response.getPageSize()).isEqualTo(2);
            assertThat(response.getTotalElements()).isEqualTo(6);
            assertThat(response.getTotalPages()).isEqualTo(3);
            assertThat(response.isLast()).isFalse();
        }

        @Test
        @DisplayName("flags the last page")
        void shouldFlagTheLastPage() {
            Pageable pageable = PageRequest.of(2, 2);
            Page<Product> page = new PageImpl<>(List.of(TestDataFactory.product()), pageable, 5);

            when(productRepository.findAll(pageable)).thenReturn(page);
            when(modelMapper.map(any(Product.class), eq(ProductResponse.class)))
                    .thenReturn(TestDataFactory.productResponse());

            assertThat(productService.getAllProducts(pageable).isLast()).isTrue();
        }

        @Test
        @DisplayName("returns an empty page instead of null when there is no product")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(productRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            PageResponse<ProductResponse> response = productService.getAllProducts(pageable);

            assertThat(response.getContent()).isNotNull().isEmpty();
            assertThat(response.getTotalElements()).isZero();
            assertThat(response.getTotalPages()).isZero();
            assertThat(response.isLast()).isTrue();
            verifyNoInteractions(modelMapper);
        }

        @Test
        @DisplayName("forwards the pageable verbatim to the repository")
        void shouldForwardPageable() {
            Pageable pageable = PageRequest.of(4, 25, Sort.by(Sort.Direction.DESC, "price"));
            when(productRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            productService.getAllProducts(pageable);

            verify(productRepository).findAll(pageable);
        }
    }

    // getProductDetails ------------------------------------------------------------------

    @Nested
    @DisplayName("getProductDetails")
    class GetProductDetails {

        @Test
        @DisplayName("returns the mapped product")
        void shouldReturnProduct() {
            Product product = TestDataFactory.product();
            ProductResponse expected = TestDataFactory.productResponse();

            when(productRepository.findById(TestDataFactory.PRODUCT_ID)).thenReturn(Optional.of(product));
            when(modelMapper.map(product, ProductResponse.class)).thenReturn(expected);

            assertThat(productService.getProductDetails(TestDataFactory.PRODUCT_ID)).isSameAs(expected);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for an unknown id")
        void shouldThrowWhenMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductDetails(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product not found");
        }

        @Test
        @DisplayName("is readable by anybody - no ownership check")
        void shouldNotValidateOwnership() {
            Product product = TestDataFactory.product(TestDataFactory.PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            when(productRepository.findById(TestDataFactory.PRODUCT_ID)).thenReturn(Optional.of(product));
            when(modelMapper.map(product, ProductResponse.class)).thenReturn(TestDataFactory.productResponse());

            productService.getProductDetails(TestDataFactory.PRODUCT_ID);

            verifyNoInteractions(productOwnershipValidator);
        }
    }

    // updateProduct ------------------------------------------------------------------

    @Nested
    @DisplayName("updateProduct")
    class UpdateProduct {

        @Test
        @DisplayName("overwrites every mutable field and saves the product")
        void shouldUpdateProduct() {
            Product product = TestDataFactory.product();
            ProductRequest request = TestDataFactory.productRequest();
            request.setName("JBL Charge 5");
            request.setBrand("JBL Pro");
            request.setDescription("Updated description");
            request.setPrice(new BigDecimal("12999.00"));
            request.setCategory("Audio");
            request.setStockCount(3);
            request.setIsAvailable(false);

            when(productRepository.findById(TestDataFactory.PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);
            when(modelMapper.map(product, ProductResponse.class)).thenReturn(TestDataFactory.productResponse());

            productService.updateProduct(request, TestDataFactory.PRODUCT_ID);

            assertThat(product.getName()).isEqualTo("JBL Charge 5");
            assertThat(product.getBrand()).isEqualTo("JBL Pro");
            assertThat(product.getDescription()).isEqualTo("Updated description");
            assertThat(product.getPrice()).isEqualByComparingTo("12999.00");
            assertThat(product.getCategory()).isEqualTo("Audio");
            assertThat(product.getStockCount()).isEqualTo(3);
            assertThat(product.getIsAvailable()).isFalse();
        }

        @Test
        @DisplayName("never reassigns the owner of the product")
        void shouldKeepTheOriginalOwner() {
            Product product = TestDataFactory.product();
            when(productRepository.findById(TestDataFactory.PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productRepository.save(product)).thenReturn(product);
            when(modelMapper.map(product, ProductResponse.class)).thenReturn(TestDataFactory.productResponse());

            UserContext.setUserId(TestDataFactory.ADMIN_ID);
            productService.updateProduct(TestDataFactory.productRequest(), TestDataFactory.PRODUCT_ID);

            assertThat(product.getCreatedBy()).isEqualTo(TestDataFactory.ADMIN_ID);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for an unknown id")
        void shouldThrowWhenMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(TestDataFactory.productRequest(), 404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product is not found");

            verify(productRepository, never()).save(any());
            verifyNoInteractions(productOwnershipValidator);
        }

        @Test
        @DisplayName("refuses to update a product owned by another admin")
        void shouldRejectForeignProduct() {
            Product product = TestDataFactory.product(TestDataFactory.PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            when(productRepository.findById(TestDataFactory.PRODUCT_ID)).thenReturn(Optional.of(product));
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productOwnershipValidator).validate(product);

            assertThatThrownBy(() ->
                    productService.updateProduct(TestDataFactory.productRequest(), TestDataFactory.PRODUCT_ID))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("You are not authorised to modify this product");

            verify(productRepository, never()).save(any());
        }
    }

    // deleteProduct ------------------------------------------------------------------

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProduct {

        @Test
        @DisplayName("validates the ownership and deletes the product")
        void shouldDeleteProduct() {
            Product product = TestDataFactory.product();
            when(productRepository.findById(TestDataFactory.PRODUCT_ID)).thenReturn(Optional.of(product));

            productService.deleteProduct(TestDataFactory.PRODUCT_ID);

            verify(productOwnershipValidator).validate(product);
            verify(productRepository).delete(product);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for an unknown id")
        void shouldThrowWhenMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product is not found");

            verify(productRepository, never()).delete(any());
        }

        @Test
        @DisplayName("refuses to delete a product owned by another admin")
        void shouldRejectForeignProduct() {
            Product product = TestDataFactory.product(TestDataFactory.PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            when(productRepository.findById(TestDataFactory.PRODUCT_ID)).thenReturn(Optional.of(product));
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productOwnershipValidator).validate(product);

            assertThatThrownBy(() -> productService.deleteProduct(TestDataFactory.PRODUCT_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verify(productRepository, never()).delete(any());
        }
    }
}


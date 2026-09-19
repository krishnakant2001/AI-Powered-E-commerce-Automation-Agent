package com.strikerkk.aicommerce.product_service.controller;

import com.strikerkk.aicommerce.product_service.common.PageResponse;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.product_service.service.ProductCartService;
import com.strikerkk.aicommerce.product_service.service.ProductService;
import com.strikerkk.aicommerce.product_service.support.TestDataFactory;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController")
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductCartService productCartService;

    @InjectMocks
    private ProductController productController;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PageResponse<ProductResponse> page(List<ProductResponse> content) {
        return PageResponse.<ProductResponse>builder()
                .content(content)
                .pageNumber(0)
                .pageSize(10)
                .totalElements(content.size())
                .totalPages(1)
                .last(true)
                .build();
    }

    // GET /products/all ---------------------------------------------------------------

    @Nested
    @DisplayName("GET /products/all")
    class GetAllProducts {

        @Test
        @DisplayName("returns 200 together with the paginated envelope")
        void shouldReturnAllProducts() throws Exception {
            when(productService.getAllProducts(any(Pageable.class)))
                    .thenReturn(page(List.of(TestDataFactory.productResponse())));

            mockMvc.perform(get("/products/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully fetch all products"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.content[0].name").value(TestDataFactory.PRODUCT_NAME))
                    .andExpect(jsonPath("$.data.content[0].brand").value(TestDataFactory.BRAND))
                    .andExpect(jsonPath("$.data.pageNumber").value(0))
                    .andExpect(jsonPath("$.data.pageSize").value(10))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.data.last").value(true));
        }

        @Test
        @DisplayName("applies the @PageableDefault of 10 items sorted by id")
        void shouldApplyTheDefaultPageable() throws Exception {
            when(productService.getAllProducts(any(Pageable.class)))
                    .thenReturn(page(Collections.emptyList()));

            mockMvc.perform(get("/products/all")).andExpect(status().isOk());

            verify(productService).getAllProducts(pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort()).isEqualTo(Sort.by("id"));
        }

        @Test
        @DisplayName("honours the page, size and sort request parameters")
        void shouldHonourTheRequestParameters() throws Exception {
            when(productService.getAllProducts(any(Pageable.class)))
                    .thenReturn(page(Collections.emptyList()));

            mockMvc.perform(get("/products/all")
                            .param("page", "2")
                            .param("size", "5")
                            .param("sort", "price,desc"))
                    .andExpect(status().isOk());

            verify(productService).getAllProducts(pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(5);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "price"));
        }

        @Test
        @DisplayName("returns an empty content array when the catalogue is empty")
        void shouldReturnEmptyContent() throws Exception {
            when(productService.getAllProducts(any(Pageable.class)))
                    .thenReturn(page(Collections.emptyList()));

            mockMvc.perform(get("/products/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(0)));
        }
    }

    // GET /products/details/{id} -----------------------------------------------------

    @Nested
    @DisplayName("GET /products/details/{productId}")
    class GetProductDetails {

        @Test
        @DisplayName("returns 200 together with the product")
        void shouldReturnProductDetails() throws Exception {
            when(productService.getProductDetails(1L)).thenReturn(TestDataFactory.productResponse());

            mockMvc.perform(get("/products/details/{productId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully the products details"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value(TestDataFactory.PRODUCT_NAME))
                    .andExpect(jsonPath("$.data.price").value(8999.00))
                    .andExpect(jsonPath("$.data.category").value(TestDataFactory.CATEGORY))
                    .andExpect(jsonPath("$.data.stockCount").value(25))
                    .andExpect(jsonPath("$.data.isAvailable").value(true));
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404")
        void shouldReturn404() throws Exception {
            when(productService.getProductDetails(404L))
                    .thenThrow(new ResourceNotFoundException("Product not found"));

            mockMvc.perform(get("/products/details/{productId}", 404L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Product not found"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("never reaches the service when the product id is not a number")
        void shouldRejectNonNumericId() throws Exception {
            // the exact status depends on which resolver wins (the catch-all @ExceptionHandler of
            // GlobalExceptionHandler also matches MethodArgumentTypeMismatchException), the
            // contract that matters here is that the call is not served
            int status = mockMvc.perform(get("/products/details/{productId}", "abc"))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isGreaterThanOrEqualTo(400);
            verify(productService, never()).getProductDetails(anyLong());
        }

        @Test
        @DisplayName("forwards the path variable to the service")
        void shouldForwardThePathVariable() throws Exception {
            when(productService.getProductDetails(anyLong())).thenReturn(TestDataFactory.productResponse());

            mockMvc.perform(get("/products/details/{productId}", 77L)).andExpect(status().isOk());

            verify(productService).getProductDetails(77L);
        }
    }

    // GET /products/{productId}/variants/{variantId}/item-info ---------------------------

    @Nested
    @DisplayName("GET /products/{productId}/variants/{variantId}/item-info")
    class GetProductItemDetails {

        @Test
        @DisplayName("returns the raw item payload consumed by the other services")
        void shouldReturnTheItemPayload() throws Exception {
            when(productCartService.getProductItemDetails(1L, 100L))
                    .thenReturn(TestDataFactory.productItemResponse());

            mockMvc.perform(get("/products/{productId}/variants/{variantId}/item-info", 1L, 100L))
                    .andExpect(status().isOk())
                    // deliberately not wrapped into the ApiResponse envelope
                    .andExpect(jsonPath("$.success").doesNotExist())
                    .andExpect(jsonPath("$.productId").value(1))
                    .andExpect(jsonPath("$.productName").value(TestDataFactory.PRODUCT_NAME))
                    .andExpect(jsonPath("$.brandName").value(TestDataFactory.BRAND))
                    .andExpect(jsonPath("$.price").value(9499.00))
                    .andExpect(jsonPath("$.isAvailable").value(true))
                    .andExpect(jsonPath("$.variantId").value(100))
                    .andExpect(jsonPath("$.size").value("M"))
                    .andExpect(jsonPath("$.color").value("Black"))
                    .andExpect(jsonPath("$.inStock").value(true))
                    .andExpect(jsonPath("$.imageUrl").value(TestDataFactory.S3_KEY));
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404")
        void shouldReturn404() throws Exception {
            when(productCartService.getProductItemDetails(1L, 999L))
                    .thenThrow(new ResourceNotFoundException("Product variant is not found"));

            mockMvc.perform(get("/products/{productId}/variants/{variantId}/item-info", 1L, 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product variant is not found"));
        }

        @Test
        @DisplayName("forwards both path variables in the right order")
        void shouldForwardBothPathVariables() throws Exception {
            when(productCartService.getProductItemDetails(eq(7L), eq(70L)))
                    .thenReturn(TestDataFactory.productItemResponse());

            mockMvc.perform(get("/products/{productId}/variants/{variantId}/item-info", 7L, 70L))
                    .andExpect(status().isOk());

            verify(productCartService).getProductItemDetails(7L, 70L);
        }
    }
}




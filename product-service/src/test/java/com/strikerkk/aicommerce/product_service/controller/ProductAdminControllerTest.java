package com.strikerkk.aicommerce.product_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.product_service.dto.request.ProductImageRequest;
import com.strikerkk.aicommerce.product_service.dto.request.ProductRequest;
import com.strikerkk.aicommerce.product_service.dto.request.ProductVariantRequest;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.product_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.product_service.service.ProductImageService;
import com.strikerkk.aicommerce.product_service.service.ProductService;
import com.strikerkk.aicommerce.product_service.service.ProductVariantService;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductAdminController")
class ProductAdminControllerTest {

    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long VARIANT_ID = TestDataFactory.VARIANT_ID;
    private static final Long IMAGE_ID = TestDataFactory.IMAGE_ID;

    @Mock
    private ProductService productService;

    @Mock
    private ProductVariantService productVariantService;

    @Mock
    private ProductImageService productImageService;

    @InjectMocks
    private ProductAdminController productAdminController;

    @Captor
    private ArgumentCaptor<ProductRequest> productRequestCaptor;

    @Captor
    private ArgumentCaptor<ProductVariantRequest> variantRequestCaptor;

    @Captor
    private ArgumentCaptor<ProductImageRequest> imageRequestCaptor;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(productAdminController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile("image", "flip6.png", MediaType.IMAGE_PNG_VALUE, "bytes".getBytes());
    }

    private MockMultipartFile isPrimaryPart(String value) {
        return new MockMultipartFile("isPrimary", "", MediaType.TEXT_PLAIN_VALUE, value.getBytes());
    }

    // POST /admin/products/create ------------------------------------------------------

    @Nested
    @DisplayName("POST /admin/products/create")
    class CreateProduct {

        @Test
        @DisplayName("returns 201 together with the created product")
        void shouldCreateProduct() throws Exception {
            when(productService.createProduct(any(ProductRequest.class)))
                    .thenReturn(TestDataFactory.productResponse());

            mockMvc.perform(post("/admin/products/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Product created successfully"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value(TestDataFactory.PRODUCT_NAME));
        }

        @Test
        @DisplayName("binds every field of the payload onto the request object")
        void shouldBindEveryField() throws Exception {
            when(productService.createProduct(any(ProductRequest.class)))
                    .thenReturn(TestDataFactory.productResponse());

            mockMvc.perform(post("/admin/products/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                    .andExpect(status().isCreated());

            verify(productService).createProduct(productRequestCaptor.capture());
            ProductRequest bound = productRequestCaptor.getValue();
            assertThat(bound.getName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(bound.getBrand()).isEqualTo(TestDataFactory.BRAND);
            assertThat(bound.getDescription()).isEqualTo("Portable waterproof bluetooth speaker");
            assertThat(bound.getPrice()).isEqualByComparingTo(TestDataFactory.PRICE);
            assertThat(bound.getCategory()).isEqualTo(TestDataFactory.CATEGORY);
            assertThat(bound.getStockCount()).isEqualTo(25);
            assertThat(bound.getIsAvailable()).isTrue();
        }

        @Test
        @DisplayName("returns 400 when the name is missing")
        void shouldRejectMissingName() throws Exception {
            ProductRequest request = TestDataFactory.productRequest();
            request.setName("  ");

            mockMvc.perform(post("/admin/products/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("name: Product name is required"));

            verify(productService, never()).createProduct(any());
        }

        @Test
        @DisplayName("returns 400 when the brand is missing")
        void shouldRejectMissingBrand() throws Exception {
            ProductRequest request = TestDataFactory.productRequest();
            request.setBrand(null);

            mockMvc.perform(post("/admin/products/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("brand: Brand is required"));
        }

        @Test
        @DisplayName("returns 400 when the price is zero")
        void shouldRejectZeroPrice() throws Exception {
            ProductRequest request = TestDataFactory.productRequest();
            request.setPrice(BigDecimal.ZERO);

            mockMvc.perform(post("/admin/products/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("price: Price must be greater than 0"));
        }

        @Test
        @DisplayName("returns 400 when the stock count is negative")
        void shouldRejectNegativeStock() throws Exception {
            ProductRequest request = TestDataFactory.productRequest();
            request.setStockCount(-1);

            mockMvc.perform(post("/admin/products/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("stockCount: Stock count cannot be negative"));
        }

        @Test
        @DisplayName("returns 400 when the category is missing")
        void shouldRejectMissingCategory() throws Exception {
            ProductRequest request = TestDataFactory.productRequest();
            request.setCategory("");

            mockMvc.perform(post("/admin/products/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("category: Category is required"));
        }
    }

    // PUT /admin/products/update/{productId} -------------------------------------------

    @Nested
    @DisplayName("PUT /admin/products/update/{productId}")
    class UpdateProduct {

        @Test
        @DisplayName("returns 200 together with the updated product")
        void shouldUpdateProduct() throws Exception {
            when(productService.updateProduct(any(ProductRequest.class), eq(PRODUCT_ID)))
                    .thenReturn(TestDataFactory.productResponse());

            mockMvc.perform(put("/admin/products/update/{productId}", PRODUCT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Product updated successfully"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404")
        void shouldReturn404() throws Exception {
            when(productService.updateProduct(any(ProductRequest.class), eq(404L)))
                    .thenThrow(new ResourceNotFoundException("Product is not found"));

            mockMvc.perform(put("/admin/products/update/{productId}", 404L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product is not found"));
        }

        @Test
        @DisplayName("maps UnauthorizedException onto 403")
        void shouldReturn403() throws Exception {
            when(productService.updateProduct(any(ProductRequest.class), anyLong()))
                    .thenThrow(new UnauthorizedException("You are not authorised to modify this product"));

            mockMvc.perform(put("/admin/products/update/{productId}", PRODUCT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("You are not authorised to modify this product"));
        }

        @Test
        @DisplayName("validates the payload before it reaches the service")
        void shouldValidateThePayload() throws Exception {
            ProductRequest request = TestDataFactory.productRequest();
            request.setPrice(null);

            mockMvc.perform(put("/admin/products/update/{productId}", PRODUCT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("price: Price is required"));

            verify(productService, never()).updateProduct(any(), anyLong());
        }
    }

    // DELETE /admin/products/delete/{productId} ----------------------------------------

    @Nested
    @DisplayName("DELETE /admin/products/delete/{productId}")
    class DeleteProduct {

        @Test
        @DisplayName("returns 204 with no body")
        void shouldDeleteProduct() throws Exception {
            doNothing().when(productService).deleteProduct(PRODUCT_ID);

            mockMvc.perform(delete("/admin/products/delete/{productId}", PRODUCT_ID))
                    .andExpect(status().isNoContent());

            verify(productService).deleteProduct(PRODUCT_ID);
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404")
        void shouldReturn404() throws Exception {
            doThrow(new ResourceNotFoundException("Product is not found"))
                    .when(productService).deleteProduct(404L);

            mockMvc.perform(delete("/admin/products/delete/{productId}", 404L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product is not found"));
        }

        @Test
        @DisplayName("maps UnauthorizedException onto 403")
        void shouldReturn403() throws Exception {
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productService).deleteProduct(PRODUCT_ID);

            mockMvc.perform(delete("/admin/products/delete/{productId}", PRODUCT_ID))
                    .andExpect(status().isForbidden());
        }
    }

    // POST /admin/products/{productId}/add/variants ------------------------------------

    @Nested
    @DisplayName("POST /admin/products/{productId}/add/variants")
    class CreateVariant {

        @Test
        @DisplayName("returns 201 together with the created variant")
        void shouldCreateVariant() throws Exception {
            when(productVariantService.createProductVariant(any(ProductVariantRequest.class), eq(PRODUCT_ID)))
                    .thenReturn(TestDataFactory.productVariantResponse());

            mockMvc.perform(post("/admin/products/{productId}/add/variants", PRODUCT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productVariantRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("Product variant created successfully"))
                    .andExpect(jsonPath("$.data.id").value(100))
                    .andExpect(jsonPath("$.data.size").value("M"))
                    .andExpect(jsonPath("$.data.color").value("Black"))
                    .andExpect(jsonPath("$.data.stockCount").value(10))
                    .andExpect(jsonPath("$.data.priceOverride").value(9499.00));
        }

        @Test
        @DisplayName("binds every field of the payload onto the request object")
        void shouldBindEveryField() throws Exception {
            when(productVariantService.createProductVariant(any(ProductVariantRequest.class), anyLong()))
                    .thenReturn(TestDataFactory.productVariantResponse());

            mockMvc.perform(post("/admin/products/{productId}/add/variants", PRODUCT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productVariantRequest())))
                    .andExpect(status().isCreated());

            verify(productVariantService).createProductVariant(variantRequestCaptor.capture(), eq(PRODUCT_ID));
            ProductVariantRequest bound = variantRequestCaptor.getValue();
            assertThat(bound.getSize()).isEqualTo("M");
            assertThat(bound.getColor()).isEqualTo("Black");
            assertThat(bound.getStockCount()).isEqualTo(10);
            assertThat(bound.getPriceOverride()).isEqualByComparingTo(TestDataFactory.PRICE_OVERRIDE);
        }

        @Test
        @DisplayName("returns 400 when the stock count is missing")
        void shouldRejectMissingStockCount() throws Exception {
            ProductVariantRequest request = TestDataFactory.productVariantRequest();
            request.setStockCount(null);

            mockMvc.perform(post("/admin/products/{productId}/add/variants", PRODUCT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("stockCount: Stock count is required"));

            verify(productVariantService, never()).createProductVariant(any(), anyLong());
        }

        @Test
        @DisplayName("returns 400 when the price override is zero")
        void shouldRejectZeroPriceOverride() throws Exception {
            ProductVariantRequest request = TestDataFactory.productVariantRequest();
            request.setPriceOverride(BigDecimal.ZERO);

            mockMvc.perform(post("/admin/products/{productId}/add/variants", PRODUCT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("priceOverride: Price override must be greater than 0"));
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404")
        void shouldReturn404() throws Exception {
            when(productVariantService.createProductVariant(any(ProductVariantRequest.class), eq(404L)))
                    .thenThrow(new ResourceNotFoundException("Product not found"));

            mockMvc.perform(post("/admin/products/{productId}/add/variants", 404L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productVariantRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product not found"));
        }
    }

    // PUT /admin/products/{productId}/update/variants/{variantId} -------------------

    @Nested
    @DisplayName("PUT /admin/products/{productId}/update/variants/{variantId}")
    class UpdateVariant {

        @Test
        @DisplayName("returns 200 together with the updated variant")
        void shouldUpdateVariant() throws Exception {
            when(productVariantService.updateProductVariant(
                    any(ProductVariantRequest.class), eq(PRODUCT_ID), eq(VARIANT_ID)))
                    .thenReturn(TestDataFactory.productVariantResponse());

            mockMvc.perform(put("/admin/products/{productId}/update/variants/{variantId}",
                            PRODUCT_ID, VARIANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productVariantRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Product variant updated successfully"))
                    .andExpect(jsonPath("$.data.id").value(100));

            verify(productVariantService)
                    .updateProductVariant(any(ProductVariantRequest.class), eq(PRODUCT_ID), eq(VARIANT_ID));
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404 when the variant is unknown")
        void shouldReturn404() throws Exception {
            when(productVariantService.updateProductVariant(
                    any(ProductVariantRequest.class), eq(PRODUCT_ID), eq(999L)))
                    .thenThrow(new ResourceNotFoundException("Variant not found for this product"));

            mockMvc.perform(put("/admin/products/{productId}/update/variants/{variantId}", PRODUCT_ID, 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(TestDataFactory.productVariantRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Variant not found for this product"));
        }

        @Test
        @DisplayName("validates the payload before it reaches the service")
        void shouldValidateThePayload() throws Exception {
            ProductVariantRequest request = TestDataFactory.productVariantRequest();
            request.setStockCount(-5);

            mockMvc.perform(put("/admin/products/{productId}/update/variants/{variantId}",
                            PRODUCT_ID, VARIANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("stockCount: Stock count cannot be negative"));
        }
    }

    // DELETE /admin/products/{productId}/delete/variants/{variantId} ---------------

    @Nested
    @DisplayName("DELETE /admin/products/{productId}/delete/variants/{variantId}")
    class DeleteVariant {

        @Test
        @DisplayName("returns 204 with no body")
        void shouldDeleteVariant() throws Exception {
            mockMvc.perform(delete("/admin/products/{productId}/delete/variants/{variantId}",
                            PRODUCT_ID, VARIANT_ID))
                    .andExpect(status().isNoContent());

            verify(productVariantService).deleteProductVariant(PRODUCT_ID, VARIANT_ID);
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404")
        void shouldReturn404() throws Exception {
            doThrow(new ResourceNotFoundException("Variant not found for this product"))
                    .when(productVariantService).deleteProductVariant(PRODUCT_ID, 999L);

            mockMvc.perform(delete("/admin/products/{productId}/delete/variants/{variantId}", PRODUCT_ID, 999L))
                    .andExpect(status().isNotFound());
        }
    }

    // POST /admin/products/{productId}/add/images --------------------------------------

    @Nested
    @DisplayName("POST /admin/products/{productId}/add/images")
    class CreateImage {

        @Test
        @DisplayName("returns 201 together with the created image")
        void shouldCreateImage() throws Exception {
            when(productImageService.addProductImage(any(ProductImageRequest.class), eq(PRODUCT_ID)))
                    .thenReturn(TestDataFactory.productImageResponse(true));

            mockMvc.perform(multipart("/admin/products/{productId}/add/images", PRODUCT_ID)
                            .file(imagePart())
                            .file(isPrimaryPart("true")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("Product image created successfully"))
                    .andExpect(jsonPath("$.data.id").value(200))
                    .andExpect(jsonPath("$.data.imageUrl").value(TestDataFactory.S3_KEY))
                    .andExpect(jsonPath("$.data.isPrimary").value(true));
        }

        @Test
        @DisplayName("forwards the uploaded file and the parsed isPrimary flag")
        void shouldForwardTheParts() throws Exception {
            when(productImageService.addProductImage(any(ProductImageRequest.class), anyLong()))
                    .thenReturn(TestDataFactory.productImageResponse(true));

            mockMvc.perform(multipart("/admin/products/{productId}/add/images", PRODUCT_ID)
                            .file(imagePart())
                            .file(isPrimaryPart("true")))
                    .andExpect(status().isCreated());

            verify(productImageService).addProductImage(imageRequestCaptor.capture(), eq(PRODUCT_ID));
            ProductImageRequest bound = imageRequestCaptor.getValue();
            assertThat(bound.getIsPrimary()).isTrue();
            assertThat(bound.getImage()).isNotNull();
            assertThat(bound.getImage().getOriginalFilename()).isEqualTo("flip6.png");
            assertThat(bound.getImage().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        }

        @Test
        @DisplayName("parses isPrimary=false")
        void shouldParseFalse() throws Exception {
            when(productImageService.addProductImage(any(ProductImageRequest.class), anyLong()))
                    .thenReturn(TestDataFactory.productImageResponse(false));

            mockMvc.perform(multipart("/admin/products/{productId}/add/images", PRODUCT_ID)
                            .file(imagePart())
                            .file(isPrimaryPart("false")))
                    .andExpect(status().isCreated());

            verify(productImageService).addProductImage(imageRequestCaptor.capture(), eq(PRODUCT_ID));
            assertThat(imageRequestCaptor.getValue().getIsPrimary()).isFalse();
        }

        @Test
        @DisplayName("treats any unparseable isPrimary value as false")
        void shouldTreatGarbageAsFalse() throws Exception {
            when(productImageService.addProductImage(any(ProductImageRequest.class), anyLong()))
                    .thenReturn(TestDataFactory.productImageResponse(false));

            mockMvc.perform(multipart("/admin/products/{productId}/add/images", PRODUCT_ID)
                            .file(imagePart())
                            .file(isPrimaryPart("yes-please")))
                    .andExpect(status().isCreated());

            verify(productImageService).addProductImage(imageRequestCaptor.capture(), eq(PRODUCT_ID));
            assertThat(imageRequestCaptor.getValue().getIsPrimary()).isFalse();
        }

        @Test
        @DisplayName("returns 400 when the image part is missing")
        void shouldRejectMissingImagePart() throws Exception {
            mockMvc.perform(multipart("/admin/products/{productId}/add/images", PRODUCT_ID)
                            .file(isPrimaryPart("true")))
                    .andExpect(status().is4xxClientError());

            verify(productImageService, never()).addProductImage(any(), anyLong());
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404")
        void shouldReturn404() throws Exception {
            when(productImageService.addProductImage(any(ProductImageRequest.class), eq(404L)))
                    .thenThrow(new ResourceNotFoundException("Product not found"));

            mockMvc.perform(multipart("/admin/products/{productId}/add/images", 404L)
                            .file(imagePart())
                            .file(isPrimaryPart("true")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product not found"));
        }
    }

    // PUT /admin/products/{productId}/update/images/{imageId}/primaryImage ---------

    @Nested
    @DisplayName("PUT /admin/products/{productId}/update/images/{imageId}/primaryImage")
    class UpdateImage {

        @Test
        @DisplayName("returns 200 together with the updated image")
        void shouldUpdateImage() throws Exception {
            when(productImageService.updateProductImage(
                    any(ProductImageRequest.class), eq(PRODUCT_ID), eq(IMAGE_ID)))
                    .thenReturn(TestDataFactory.productImageResponse(true));

            mockMvc.perform(multipart(HttpMethod.PUT,
                            "/admin/products/{productId}/update/images/{imageId}/primaryImage",
                            PRODUCT_ID, IMAGE_ID)
                            .file(imagePart())
                            .file(isPrimaryPart("true")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Product image updated successfully"))
                    .andExpect(jsonPath("$.data.isPrimary").value(true));
        }

        @Test
        @DisplayName("forwards both path variables and the parsed flag")
        void shouldForwardThePathVariables() throws Exception {
            when(productImageService.updateProductImage(any(ProductImageRequest.class), anyLong(), anyLong()))
                    .thenReturn(TestDataFactory.productImageResponse(false));

            mockMvc.perform(multipart(HttpMethod.PUT,
                            "/admin/products/{productId}/update/images/{imageId}/primaryImage", 9L, 99L)
                            .file(imagePart())
                            .file(isPrimaryPart("false")))
                    .andExpect(status().isOk());

            verify(productImageService)
                    .updateProductImage(imageRequestCaptor.capture(), eq(9L), eq(99L));
            assertThat(imageRequestCaptor.getValue().getIsPrimary()).isFalse();
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404 when the image is unknown")
        void shouldReturn404() throws Exception {
            when(productImageService.updateProductImage(
                    any(ProductImageRequest.class), eq(PRODUCT_ID), eq(999L)))
                    .thenThrow(new ResourceNotFoundException("Image not found for this product"));

            mockMvc.perform(multipart(HttpMethod.PUT,
                            "/admin/products/{productId}/update/images/{imageId}/primaryImage",
                            PRODUCT_ID, 999L)
                            .file(imagePart())
                            .file(isPrimaryPart("true")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Image not found for this product"));
        }
    }

    // DELETE /admin/products/{productId}/delete/images/{imageId} ---------------

    @Nested
    @DisplayName("DELETE /admin/products/{productId}/delete/images/{imageId}")
    class DeleteImage {

        @Test
        @DisplayName("returns 200 and, unlike the other delete endpoints, keeps the envelope")
        void shouldDeleteImage() throws Exception {
            mockMvc.perform(delete("/admin/products/{productId}/delete/images/{imageId}",
                            PRODUCT_ID, IMAGE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Product image deleted successfully"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(productImageService).deleteProductImage(PRODUCT_ID, IMAGE_ID);
        }

        @Test
        @DisplayName("maps ResourceNotFoundException onto 404")
        void shouldReturn404() throws Exception {
            doThrow(new ResourceNotFoundException("Image not found for this product"))
                    .when(productImageService).deleteProductImage(PRODUCT_ID, 999L);

            mockMvc.perform(delete("/admin/products/{productId}/delete/images/{imageId}", PRODUCT_ID, 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("maps UnauthorizedException onto 403")
        void shouldReturn403() throws Exception {
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productImageService).deleteProductImage(PRODUCT_ID, IMAGE_ID);

            mockMvc.perform(delete("/admin/products/{productId}/delete/images/{imageId}",
                            PRODUCT_ID, IMAGE_ID))
                    .andExpect(status().isForbidden());
        }
    }
}


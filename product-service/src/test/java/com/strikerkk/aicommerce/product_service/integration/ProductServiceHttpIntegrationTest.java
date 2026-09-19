package com.strikerkk.aicommerce.product_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.common.PageResponse;
import com.strikerkk.aicommerce.product_service.dto.request.ProductImageRequest;
import com.strikerkk.aicommerce.product_service.dto.request.ProductRequest;
import com.strikerkk.aicommerce.product_service.dto.request.ProductVariantRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.product_service.service.ProductCartService;
import com.strikerkk.aicommerce.product_service.service.ProductImageService;
import com.strikerkk.aicommerce.product_service.service.ProductService;
import com.strikerkk.aicommerce.product_service.service.ProductVariantService;
import com.strikerkk.aicommerce.product_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Product service HTTP wiring")
class ProductServiceHttpIntegrationTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_ROLE_HEADER = "X-user-role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductVariantService productVariantService;

    @MockitoBean
    private ProductImageService productImageService;

    @MockitoBean
    private ProductCartService productCartService;

    /** Never talk to AWS from a test. */
    @MockitoBean
    private S3Client s3Client;

    private PageResponse<ProductResponse> emptyPage() {
        return PageResponse.<ProductResponse>builder()
                .content(Collections.emptyList())
                .pageNumber(0).pageSize(10).totalElements(0).totalPages(0).last(true)
                .build();
    }

    /** A denied call must never reach the service, whichever status the advice ends up producing. */
    private void assertNotSuccessful(MvcResult result) {
        assertThat(result.getResponse().getStatus())
                .as("the request must not be served")
                .isGreaterThanOrEqualTo(400);
    }

    // public routes ------------------------------------------------------------------

    @Test
    @DisplayName("the catalogue listing is public")
    void catalogueListingIsPublic() throws Exception {
        when(productService.getAllProducts(any())).thenReturn(emptyPage());

        mockMvc.perform(get("/products/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("the product details endpoint is public")
    void productDetailsIsPublic() throws Exception {
        when(productService.getProductDetails(1L)).thenReturn(TestDataFactory.productResponse());

        mockMvc.perform(get("/products/details/{productId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(TestDataFactory.PRODUCT_NAME));
    }

    @Test
    @DisplayName("the item-info endpoint used by cart-service is public")
    void itemInfoIsPublic() throws Exception {
        when(productCartService.getProductItemDetails(1L, 100L))
                .thenReturn(TestDataFactory.productItemResponse());

        mockMvc.perform(get("/products/{productId}/variants/{variantId}/item-info", 1L, 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.inStock").value(true));
    }

    // administrative routes ---------------------------------------------------------

    @Test
    @DisplayName("an ADMIN forwarded by the gateway can create a product")
    void adminCanCreateProduct() throws Exception {
        when(productService.createProduct(any(ProductRequest.class)))
                .thenReturn(TestDataFactory.productResponse());

        mockMvc.perform(post("/admin/products/create")
                        .header(USER_ID_HEADER, TestDataFactory.ADMIN_ID)
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Product created successfully"));
    }

    @Test
    @DisplayName("a USER cannot create a product")
    void userCannotCreateProduct() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/products/create")
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                .andReturn();

        assertNotSuccessful(result);
        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("an anonymous caller cannot create a product")
    void anonymousCannotCreateProduct() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/products/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                .andReturn();

        assertNotSuccessful(result);
        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("a USER cannot delete a product")
    void userCannotDeleteProduct() throws Exception {
        MvcResult result = mockMvc.perform(delete("/admin/products/delete/{productId}", 1L)
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "USER"))
                .andReturn();

        assertNotSuccessful(result);
        verify(productService, never()).deleteProduct(anyLong());
    }

    @Test
    @DisplayName("a USER cannot add a variant")
    void userCannotAddVariant() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/products/{productId}/add/variants", 1L)
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.productVariantRequest())))
                .andReturn();

        assertNotSuccessful(result);
        verify(productVariantService, never()).createProductVariant(any(), anyLong());
    }

    @Test
    @DisplayName("a USER cannot upload an image")
    void userCannotUploadImage() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/admin/products/{productId}/add/images", 1L)
                        .file(new MockMultipartFile("image", "a.png", MediaType.IMAGE_PNG_VALUE, "b".getBytes()))
                        .file(new MockMultipartFile("isPrimary", "", MediaType.TEXT_PLAIN_VALUE, "true".getBytes()))
                        .header(USER_ID_HEADER, "42")
                        .header(USER_ROLE_HEADER, "USER"))
                .andReturn();

        assertNotSuccessful(result);
        verify(productImageService, never()).addProductImage(any(), anyLong());
    }

    @Test
    @DisplayName("an ADMIN can manage the variants and the images")
    void adminCanManageVariantsAndImages() throws Exception {
        when(productVariantService.updateProductVariant(any(ProductVariantRequest.class), anyLong(), anyLong()))
                .thenReturn(TestDataFactory.productVariantResponse());
        when(productImageService.addProductImage(any(ProductImageRequest.class), anyLong()))
                .thenReturn(TestDataFactory.productImageResponse(true));

        mockMvc.perform(put("/admin/products/{productId}/update/variants/{variantId}", 1L, 100L)
                        .header(USER_ID_HEADER, TestDataFactory.ADMIN_ID)
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.productVariantRequest())))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/admin/products/{productId}/add/images", 1L)
                        .file(new MockMultipartFile("image", "a.png", MediaType.IMAGE_PNG_VALUE, "b".getBytes()))
                        .file(new MockMultipartFile("isPrimary", "", MediaType.TEXT_PLAIN_VALUE, "true".getBytes()))
                        .header(USER_ID_HEADER, TestDataFactory.ADMIN_ID)
                        .header(USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isCreated());
    }

    // header propagation -------------------------------------------------------------

    @Test
    @DisplayName("the gateway headers reach the service through the UserContext")
    void gatewayHeadersReachTheService() throws Exception {
        AtomicReference<String> seenUserId = new AtomicReference<>();
        AtomicReference<String> seenUserRole = new AtomicReference<>();

        when(productService.createProduct(any(ProductRequest.class))).thenAnswer(invocation -> {
            seenUserId.set(UserContext.getUserId());
            seenUserRole.set(UserContext.getUserRole());
            return TestDataFactory.productResponse();
        });

        mockMvc.perform(post("/admin/products/create")
                        .header(USER_ID_HEADER, "admin-77")
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                .andExpect(status().isCreated());

        assertThat(seenUserId.get()).isEqualTo("admin-77");
        assertThat(seenUserRole.get()).isEqualTo("ADMIN");
    }

    // error mapping ------------------------------------------------------------------

    @Test
    @DisplayName("bean validation failures are rendered as 400 by the advice")
    void validationFailuresAreRenderedAs400() throws Exception {
        ProductRequest invalid = TestDataFactory.productRequest();
        invalid.setName(null);

        mockMvc.perform(post("/admin/products/create")
                        .header(USER_ID_HEADER, TestDataFactory.ADMIN_ID)
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("name: Product name is required"));

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("ResourceNotFoundException is rendered as 404 by the advice")
    void notFoundIsRenderedAs404() throws Exception {
        when(productService.getProductDetails(404L))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(get("/products/details/{productId}", 404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("UnauthorizedException is rendered as 403 by the advice")
    void ownershipFailureIsRenderedAs403() throws Exception {
        when(productService.updateProduct(any(ProductRequest.class), anyLong()))
                .thenThrow(new UnauthorizedException("You are not authorised to modify this product"));

        mockMvc.perform(put("/admin/products/update/{productId}", 1L)
                        .header(USER_ID_HEADER, TestDataFactory.OTHER_ADMIN_ID)
                        .header(USER_ROLE_HEADER, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataFactory.productRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not authorised to modify this product"));
    }

    @Test
    @DisplayName("the paging query parameters survive the whole pipeline")
    void pagingParametersSurviveThePipeline() throws Exception {
        when(productService.getAllProducts(any())).thenReturn(emptyPage());

        mockMvc.perform(get("/products/all")
                        .param("page", "1")
                        .param("size", "3")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk());

        verify(productService).getAllProducts(org.mockito.ArgumentMatchers.argThat(pageable ->
                pageable.getPageNumber() == 1
                        && pageable.getPageSize() == 3
                        && pageable.getSort().getOrderFor("name") != null));
    }

    @Test
    @DisplayName("an unknown route returns 404 and not a 500")
    void unknownRouteReturns404() throws Exception {
        MvcResult result = mockMvc.perform(get("/products/does-not-exist")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("the catalogue listing returns the whole envelope")
    void catalogueListingReturnsTheWholeEnvelope() throws Exception {
        when(productService.getAllProducts(any())).thenReturn(PageResponse.<ProductResponse>builder()
                .content(List.of(TestDataFactory.productResponse()))
                .pageNumber(0).pageSize(10).totalElements(1).totalPages(1).last(true)
                .build());

        mockMvc.perform(get("/products/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.last").value(true));
    }
}


package com.strikerkk.aicommerce.cart_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.cart_service.auth.UserContext;
import com.strikerkk.aicommerce.cart_service.clients.ProductClient;
import com.strikerkk.aicommerce.cart_service.entity.Cart;
import com.strikerkk.aicommerce.cart_service.entity.CartItem;
import com.strikerkk.aicommerce.cart_service.repository.CartItemRepository;
import com.strikerkk.aicommerce.cart_service.repository.CartRepository;
import com.strikerkk.aicommerce.cart_service.support.TestDataFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Cart service HTTP wiring")
class CartServiceHttpIntegrationTest {

    private static final String USER_ID_HEADER = "X-user-id";
    private static final String USER_ID = "42";
    private static final String OTHER_USER_ID = "99";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("product-service-call").reset();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
    }

    private String addBody(long productId, long variantId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("productId", productId, "variantId", variantId, "quantity", quantity));
    }

    private void productServiceAnswers(BigDecimal price, boolean available, boolean inStock) {
        when(productClient.getProductItemDetails(anyLong(), anyLong()))
                .thenReturn(TestDataFactory.productCartResponse(price, available, inStock));
    }

    private Cart seedCartWithOneItem(Long userId, BigDecimal price, int quantity) {
        Cart cart = cartRepository.saveAndFlush(Cart.builder().userId(userId).build());
        CartItem item = CartItem.builder()
                .cart(cart)
                .productId(TestDataFactory.PRODUCT_ID)
                .variantId(TestDataFactory.VARIANT_ID)
                .quantity(quantity)
                .productName(TestDataFactory.PRODUCT_NAME)
                .productBrand(TestDataFactory.BRAND)
                .priceAtAdd(price)
                .ProductImageUrl(TestDataFactory.IMAGE_URL)
                .size(TestDataFactory.SIZE)
                .color(TestDataFactory.COLOR)
                .build();
        cart.getCartItems().add(item);
        return cartRepository.saveAndFlush(cart);
    }

    // the happy path ===================================================================

    @Nested
    @DisplayName("the full journey")
    class HappyPath {

        @Test
        @DisplayName("opening an empty cart creates it on the fly")
        void openingAnEmptyCartCreatesIt() throws Exception {
            mockMvc.perform(get("/cart").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(42))
                    .andExpect(jsonPath("$.data.items").isEmpty())
                    .andExpect(jsonPath("$.data.totalAmount").value(0));

            assertThat(cartRepository.findByUserId(42L)).isPresent();
        }

        @Test
        @DisplayName("add, read, update and delete an item end to end")
        void addReadUpdateDelete() throws Exception {
            productServiceAnswers(new BigDecimal("8999.00"), true, true);

            // add
            mockMvc.perform(post("/cart/items")
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(addBody(1L, 2L, 2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.totalItems").value(2))
                    .andExpect(jsonPath("$.data.totalAmount").value(17998.00));

            Long itemId = cartItemRepository.findAll().get(0).getId();

            // read
            mockMvc.perform(get("/cart").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.data.items[0].productDetails.productName")
                            .value(TestDataFactory.PRODUCT_NAME))
                    .andExpect(jsonPath("$.data.items[0].productDetails.variantName").value("M - Black"));

            // update
            mockMvc.perform(patch("/cart/items/" + itemId)
                            .header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantity\":5}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.quantity").value(5))
                    .andExpect(jsonPath("$.data.itemTotal").value(44995.00));

            // delete
            mockMvc.perform(delete("/cart/items/" + itemId).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(cartItemRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("adding the same variant twice merges the two lines")
        void addingTheSameVariantTwiceMerges() throws Exception {
            productServiceAnswers(new BigDecimal("100.00"), true, true);

            mockMvc.perform(post("/cart/items").header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(addBody(1L, 2L, 1)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/cart/items").header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(addBody(1L, 2L, 3)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.totalUniqueItems").value(1))
                    .andExpect(jsonPath("$.data.totalItems").value(4));

            assertThat(cartItemRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("GET /cart/items answers with the raw array")
        void theRawItemsEndpoint() throws Exception {
            seedCartWithOneItem(42L, new BigDecimal("100.00"), 2);
            productServiceAnswers(new BigDecimal("100.00"), true, true);

            mockMvc.perform(get("/cart/items").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].quantity").value(2))
                    .andExpect(jsonPath("$[0].itemTotal").value(200.00))
                    .andExpect(jsonPath("$.success").doesNotExist());
        }

        @Test
        @DisplayName("clearing the cart empties the lines but keeps the cart")
        void clearingTheCart() throws Exception {
            seedCartWithOneItem(42L, new BigDecimal("100.00"), 2);

            mockMvc.perform(delete("/cart/clear").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Successfully clear the cart"));

            assertThat(cartItemRepository.findAll()).isEmpty();
            assertThat(cartRepository.findByUserId(42L)).isPresent();
        }
    }

    // enrichment & degradation ===================================================================

    @Nested
    @DisplayName("enrichment")
    class Enrichment {

        @Test
        @DisplayName("a price change upstream is written back and flagged as enriched")
        void aPriceChangeIsWrittenBack() throws Exception {
            seedCartWithOneItem(42L, new BigDecimal("100.00"), 2);
            productServiceAnswers(new BigDecimal("150.00"), true, true);

            mockMvc.perform(get("/cart").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enriched").value(true))
                    .andExpect(jsonPath("$.data.items[0].priceAtAdd").value(150.00))
                    .andExpect(jsonPath("$.data.totalAmount").value(300.00));
        }

        @Test
        @DisplayName("a product that went away is dropped from the cart")
        void anUnavailableProductIsDropped() throws Exception {
            seedCartWithOneItem(42L, new BigDecimal("100.00"), 1);
            productServiceAnswers(new BigDecimal("100.00"), false, true);

            mockMvc.perform(get("/cart").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isEmpty())
                    .andExpect(jsonPath("$.data.enriched").value(true));
        }

        @Test
        @DisplayName("the cart still renders from its snapshots when product-service is down")
        void degradesGracefullyWhenProductServiceIsDown() throws Exception {
            seedCartWithOneItem(42L, new BigDecimal("100.00"), 2);
            when(productClient.getProductItemDetails(anyLong(), anyLong()))
                    .thenThrow(new RuntimeException("connection refused"));

            mockMvc.perform(get("/cart").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].priceAtAdd").value(100.00))
                    .andExpect(jsonPath("$.data.items[0].productDetails.productName")
                            .value(TestDataFactory.PRODUCT_NAME))
                    .andExpect(jsonPath("$.data.totalAmount").value(200.00))
                    .andExpect(jsonPath("$.data.enriched").value(false));
        }

        @Test
        @DisplayName("adding an item DOES fail when product-service is down - there is no price to trust")
        void addingFailsWhenProductServiceIsDown() throws Exception {
            when(productClient.getProductItemDetails(anyLong(), anyLong()))
                    .thenThrow(new RuntimeException("connection refused"));

            mockMvc.perform(post("/cart/items").header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(addBody(1L, 2L, 1)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Product Service unavailable"));

            assertThat(cartItemRepository.findAll()).isEmpty();
        }
    }

    // failures ===================================================================

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        @DisplayName("an out of stock variant is a 400 and nothing is written")
        void outOfStockIs400() throws Exception {
            productServiceAnswers(new BigDecimal("100.00"), true, false);

            mockMvc.perform(post("/cart/items").header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(addBody(1L, 2L, 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Product variant is out of stock. productId=1, variantId=2"));

            assertThat(cartItemRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("a body that violates a constraint is a 400 with the exact field message")
        void aValidationFailureIs400() throws Exception {
            mockMvc.perform(post("/cart/items").header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(addBody(1L, 2L, 0)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("quantity: Quantity must be at least 1"));
        }

        @Test
        @DisplayName("updating a line that does not exist is a 404")
        void updatingAnUnknownLineIs404() throws Exception {
            mockMvc.perform(patch("/cart/items/123456").header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Cart Item is not found"));
        }

        @Test
        @DisplayName("deleting the line of another customer is a 403 and the line survives")
        void deletingAForeignLineIs403() throws Exception {
            Cart foreignCart = seedCartWithOneItem(99L, new BigDecimal("100.00"), 1);
            Long foreignItemId = foreignCart.getCartItems().get(0).getId();

            mockMvc.perform(delete("/cart/items/" + foreignItemId).header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Unauthorized request"));

            assertThat(cartItemRepository.findById(foreignItemId)).isPresent();
        }

        @Test
        @DisplayName("clearing a cart that does not exist is a 404")
        void clearingAnUnknownCartIs404() throws Exception {
            mockMvc.perform(delete("/cart/clear").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No cart found for userId 42"));
        }

        @Test
        @DisplayName("a request without the gateway header is a 500, not a 401")
        void aRequestWithoutTheHeaderIs500() throws Exception {
            mockMvc.perform(get("/cart"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // isolation between customers ===================================================================

    @Nested
    @DisplayName("isolation between customers")
    class Isolation {

        @Test
        @DisplayName("two customers never see each other's cart")
        void twoCustomersAreIsolated() throws Exception {
            productServiceAnswers(new BigDecimal("100.00"), true, true);

            mockMvc.perform(post("/cart/items").header(USER_ID_HEADER, USER_ID)
                            .contentType(MediaType.APPLICATION_JSON).content(addBody(1L, 2L, 1)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/cart").header(USER_ID_HEADER, OTHER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(99))
                    .andExpect(jsonPath("$.data.items").isEmpty());

            mockMvc.perform(get("/cart").header(USER_ID_HEADER, USER_ID))
                    .andExpect(jsonPath("$.data.items").isNotEmpty());
        }

        @Test
        @DisplayName("clearing one cart leaves the other one untouched")
        void clearingOneCartLeavesTheOther() throws Exception {
            seedCartWithOneItem(42L, new BigDecimal("100.00"), 1);
            seedCartWithOneItem(99L, new BigDecimal("100.00"), 1);

            mockMvc.perform(delete("/cart/clear").header(USER_ID_HEADER, USER_ID))
                    .andExpect(status().isOk());

            assertThat(cartItemRepository.findAll()).hasSize(1);
            assertThat(cartRepository.findByUserId(99L)).isPresent();
        }

        @Test
        @DisplayName("the caller id is cleared once the request is over")
        void theCallerIdIsClearedAfterTheRequest() throws Exception {
            mockMvc.perform(get("/cart").header(USER_ID_HEADER, USER_ID)).andExpect(status().isOk());

            assertThat(UserContext.getUserId()).isNull();
        }
    }
}





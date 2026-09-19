package com.strikerkk.aicommerce.cart_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.cart_service.dto.request.AddCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.request.UpdateCartItemRequest;
import com.strikerkk.aicommerce.cart_service.dto.response.CartItemResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.CartResponse;
import com.strikerkk.aicommerce.cart_service.dto.response.ProductDetails;
import com.strikerkk.aicommerce.cart_service.exception.AccessDeniedException;
import com.strikerkk.aicommerce.cart_service.exception.IllegalStateException;
import com.strikerkk.aicommerce.cart_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.cart_service.exception.advice.GlobalExceptionHandler;
import com.strikerkk.aicommerce.cart_service.service.CartService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartController")
class CartControllerTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    @Captor
    private ArgumentCaptor<AddCartItemRequest> addCaptor;

    @Captor
    private ArgumentCaptor<UpdateCartItemRequest> updateCaptor;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cartController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CartItemResponse itemResponse() {
        ProductDetails details = new ProductDetails();
        details.setProductName("JBL Flip 6");
        details.setProductBrand("JBL");
        details.setProductImageUrl("products/1/primary.png");
        details.setVariantName("M - Black");
        details.setSize("M");
        details.setColor("Black");

        CartItemResponse item = new CartItemResponse();
        item.setId(500L);
        item.setProductId(1L);
        item.setVariantId(2L);
        item.setQuantity(3);
        item.setPriceAtAdd(new BigDecimal("8999.00"));
        item.setItemTotal(new BigDecimal("26997.00"));
        item.setProductDetails(details);
        return item;
    }

    private CartResponse cartResponse(CartItemResponse... items) {
        CartResponse response = new CartResponse();
        response.setCartId(7L);
        response.setUserId(42L);
        response.setItems(List.of(items));
        response.setTotalAmount(new BigDecimal("26997.00"));
        response.setTotalItems(3);
        response.setTotalUniqueItems(items.length);
        response.setEnriched(true);
        response.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        response.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        return response;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // GET /cart ===================================================================

    @Nested
    @DisplayName("GET /cart")
    class GetCart {

        @Test
        @DisplayName("returns 200 with the cart inside the envelope")
        void returnsTheCart() throws Exception {
            when(cartService.allCartItems()).thenReturn(cartResponse(itemResponse()));

            mockMvc.perform(get("/cart"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully fetch all items of user cart"))
                    .andExpect(jsonPath("$.data.cartId").value(7))
                    .andExpect(jsonPath("$.data.userId").value(42))
                    .andExpect(jsonPath("$.data.totalAmount").value(26997.00))
                    .andExpect(jsonPath("$.data.totalItems").value(3))
                    .andExpect(jsonPath("$.data.totalUniqueItems").value(1))
                    .andExpect(jsonPath("$.data.enriched").value(true))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("exposes the product details of every line")
        void exposesTheProductDetails() throws Exception {
            when(cartService.allCartItems()).thenReturn(cartResponse(itemResponse()));

            mockMvc.perform(get("/cart"))
                    .andExpect(jsonPath("$.data.items[0].id").value(500))
                    .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                    .andExpect(jsonPath("$.data.items[0].itemTotal").value(26997.00))
                    .andExpect(jsonPath("$.data.items[0].productDetails.productName").value("JBL Flip 6"))
                    .andExpect(jsonPath("$.data.items[0].productDetails.variantName").value("M - Black"));
        }

        @Test
        @DisplayName("returns 200 with an empty item array for an empty cart")
        void returnsAnEmptyCart() throws Exception {
            CartResponse empty = new CartResponse();
            empty.setCartId(7L);
            empty.setUserId(42L);
            empty.setItems(List.of());
            empty.setTotalAmount(BigDecimal.ZERO);
            empty.setTotalItems(0);
            empty.setTotalUniqueItems(0);
            when(cartService.allCartItems()).thenReturn(empty);

            mockMvc.perform(get("/cart"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items").isEmpty())
                    .andExpect(jsonPath("$.data.totalItems").value(0));
        }

        @Test
        @DisplayName("a downstream outage is rendered as a 500 with the reason")
        void aDownstreamOutageIsA500() throws Exception {
            when(cartService.allCartItems()).thenThrow(new RuntimeException("Product Service unavailable"));

            mockMvc.perform(get("/cart"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Product Service unavailable"));
        }
    }

    // GET /cart/items ===================================================================

    @Nested
    @DisplayName("GET /cart/items")
    class GetItems {

        @Test
        @DisplayName("returns the raw array, without the ApiResponse envelope")
        void returnsTheRawArray() throws Exception {
            when(cartService.allCartItems()).thenReturn(cartResponse(itemResponse()));

            mockMvc.perform(get("/cart/items"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").value(500))
                    .andExpect(jsonPath("$[0].productId").value(1))
                    .andExpect(jsonPath("$[0].variantId").value(2))
                    .andExpect(jsonPath("$[0].quantity").value(3))
                    .andExpect(jsonPath("$[0].itemTotal").value(26997.00))
                    .andExpect(jsonPath("$.success").doesNotExist());
        }

        @Test
        @DisplayName("returns an empty array for an empty cart")
        void returnsAnEmptyArray() throws Exception {
            CartResponse empty = new CartResponse();
            empty.setItems(List.of());
            when(cartService.allCartItems()).thenReturn(empty);

            mockMvc.perform(get("/cart/items"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("is served by the very same service call as GET /cart")
        void reusesTheSameServiceCall() throws Exception {
            when(cartService.allCartItems()).thenReturn(cartResponse(itemResponse()));

            mockMvc.perform(get("/cart/items")).andExpect(status().isOk());

            verify(cartService).allCartItems();
        }
    }

    // POST /cart/items ===================================================================

    @Nested
    @DisplayName("POST /cart/items")
    class AddItem {

        @Test
        @DisplayName("returns 201 with the refreshed cart")
        void returns201() throws Exception {
            when(cartService.addItemToCart(any(AddCartItemRequest.class))).thenReturn(cartResponse(itemResponse()));

            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("productId", 1, "variantId", 2, "quantity", 3))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully add item in the cart"))
                    .andExpect(jsonPath("$.data.cartId").value(7));
        }

        @Test
        @DisplayName("binds every field of the body")
        void bindsTheBody() throws Exception {
            when(cartService.addItemToCart(any(AddCartItemRequest.class))).thenReturn(cartResponse());

            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("productId", 11, "variantId", 22, "quantity", 33))))
                    .andExpect(status().isCreated());

            verify(cartService).addItemToCart(addCaptor.capture());
            assertThat(addCaptor.getValue().getProductId()).isEqualTo(11L);
            assertThat(addCaptor.getValue().getVariantId()).isEqualTo(22L);
            assertThat(addCaptor.getValue().getQuantity()).isEqualTo(33);
        }

        @Test
        @DisplayName("rejects a missing productId with 400 and the exact message")
        void rejectsAMissingProductId() throws Exception {
            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("variantId", 2, "quantity", 1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("productId: Product ID is required"));

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("rejects a missing variantId with 400")
        void rejectsAMissingVariantId() throws Exception {
            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("productId", 1, "quantity", 1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("variantId: Variant ID is required"));
        }

        @Test
        @DisplayName("rejects a missing quantity with 400")
        void rejectsAMissingQuantity() throws Exception {
            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("productId", 1, "variantId", 2))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("quantity: Quantity is required"));
        }

        @Test
        @DisplayName("rejects a quantity of 0 with 400")
        void rejectsAZeroQuantity() throws Exception {
            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("productId", 1, "variantId", 2, "quantity", 0))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("quantity: Quantity must be at least 1"));

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("an out of stock variant is a 400 that explains why")
        void anOutOfStockVariantIs400() throws Exception {
            when(cartService.addItemToCart(any(AddCartItemRequest.class)))
                    .thenThrow(new IllegalStateException("Product variant is out of stock. productId=1, variantId=2"));

            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("productId", 1, "variantId", 2, "quantity", 1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message")
                            .value("Product variant is out of stock. productId=1, variantId=2"));
        }

        @Test
        @DisplayName("an unreachable product-service is a 500")
        void anUnreachableProductServiceIs500() throws Exception {
            when(cartService.addItemToCart(any(AddCartItemRequest.class)))
                    .thenThrow(new RuntimeException("Product Service unavailable"));

            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("productId", 1, "variantId", 2, "quantity", 1))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Product Service unavailable"));
        }

        @Test
        @DisplayName("a body that is not JSON is not served")
        void rejectsAMalformedBody() throws Exception {
            mockMvc.perform(post("/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ this is not json"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(201));

            verifyNoInteractions(cartService);
        }
    }

    // PATCH /cart/items/{id} ===================================================================

    @Nested
    @DisplayName("PATCH /cart/items/{cartItemId}")
    class UpdateItem {

        @Test
        @DisplayName("returns 200 with the updated line")
        void returns200() throws Exception {
            when(cartService.updateCartItem(any(UpdateCartItemRequest.class), eq(500L))).thenReturn(itemResponse());

            mockMvc.perform(patch("/cart/items/500")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("quantity", 3))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully updated the item in cart"))
                    .andExpect(jsonPath("$.data.id").value(500))
                    .andExpect(jsonPath("$.data.quantity").value(3))
                    .andExpect(jsonPath("$.data.itemTotal").value(26997.00))
                    .andExpect(jsonPath("$.data.productDetails.variantName").value("M - Black"));
        }

        @Test
        @DisplayName("passes the path variable and the body to the service")
        void passesThePathVariableAndTheBody() throws Exception {
            when(cartService.updateCartItem(any(UpdateCartItemRequest.class), anyLong())).thenReturn(itemResponse());

            mockMvc.perform(patch("/cart/items/777")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("quantity", 9))))
                    .andExpect(status().isOk());

            verify(cartService).updateCartItem(updateCaptor.capture(), eq(777L));
            assertThat(updateCaptor.getValue().getQuantity()).isEqualTo(9);
        }

        @Test
        @DisplayName("rejects a missing quantity with 400")
        void rejectsAMissingQuantity() throws Exception {
            mockMvc.perform(patch("/cart/items/500")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("quantity: Quantity is required"));

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("rejects a quantity of 0 with 400 - a removal goes through DELETE")
        void rejectsAZeroQuantity() throws Exception {
            mockMvc.perform(patch("/cart/items/500")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("quantity", 0))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("quantity: Quantity must be at least 1"));
        }

        @Test
        @DisplayName("an unknown line is a 404")
        void anUnknownLineIs404() throws Exception {
            when(cartService.updateCartItem(any(UpdateCartItemRequest.class), anyLong()))
                    .thenThrow(new ResourceNotFoundException("Cart Item is not found"));

            mockMvc.perform(patch("/cart/items/500")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("quantity", 2))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Cart Item is not found"));
        }

        @Test
        @DisplayName("a non numeric id is not served")
        void rejectsANonNumericId() throws Exception {
            mockMvc.perform(patch("/cart/items/abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map("quantity", 2))))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));

            verify(cartService, never()).updateCartItem(any(), anyLong());
        }
    }

    // DELETE /cart/items/{id} ===================================================================

    @Nested
    @DisplayName("DELETE /cart/items/{cartItemId}")
    class DeleteItem {

        @Test
        @DisplayName("returns 200 with an envelope that carries no data")
        void returns200() throws Exception {
            doNothing().when(cartService).deleteItemFromCart(500L);

            mockMvc.perform(delete("/cart/items/500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully deleted the item from cart"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("forwards the path variable")
        void forwardsThePathVariable() throws Exception {
            doNothing().when(cartService).deleteItemFromCart(anyLong());

            mockMvc.perform(delete("/cart/items/777")).andExpect(status().isOk());

            verify(cartService).deleteItemFromCart(777L);
        }

        @Test
        @DisplayName("an unknown line is a 404")
        void anUnknownLineIs404() throws Exception {
            doThrow(new ResourceNotFoundException("Cart item not found 500"))
                    .when(cartService).deleteItemFromCart(500L);

            mockMvc.perform(delete("/cart/items/500"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Cart item not found 500"));
        }

        @Test
        @DisplayName("the line of another customer is a 403")
        void aForeignLineIs403() throws Exception {
            doThrow(new AccessDeniedException("Unauthorized request"))
                    .when(cartService).deleteItemFromCart(500L);

            mockMvc.perform(delete("/cart/items/500"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Unauthorized request"));
        }
    }

    // DELETE /cart/clear ===================================================================

    @Nested
    @DisplayName("DELETE /cart/clear")
    class ClearCart {

        @Test
        @DisplayName("returns 200 with an envelope that carries no data")
        void returns200() throws Exception {
            doNothing().when(cartService).clearCart();

            mockMvc.perform(delete("/cart/clear"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Successfully clear the cart"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(cartService).clearCart();
        }

        @Test
        @DisplayName("a customer without a cart gets a 404")
        void aCustomerWithoutACartGets404() throws Exception {
            doThrow(new ResourceNotFoundException("No cart found for userId 42")).when(cartService).clearCart();

            mockMvc.perform(delete("/cart/clear"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No cart found for userId 42"));
        }

        @Test
        @DisplayName("/cart/clear is distinct from /cart/items/{id}")
        void isDistinctFromTheItemDeletion() throws Exception {
            doNothing().when(cartService).clearCart();

            mockMvc.perform(delete("/cart/clear")).andExpect(status().isOk());

            verify(cartService).clearCart();
            verify(cartService, never()).deleteItemFromCart(anyLong());
        }
    }

    /** Tiny helper so the request bodies above stay readable. */
    private static java.util.Map<String, Object> Map(Object... keyValues) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}


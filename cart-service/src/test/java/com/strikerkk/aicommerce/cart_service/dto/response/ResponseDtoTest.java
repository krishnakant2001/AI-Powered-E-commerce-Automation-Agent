package com.strikerkk.aicommerce.cart_service.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Response DTOs")
class ResponseDtoTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Nested
    @DisplayName("CartResponse")
    class Cart {

        private CartResponse cartResponse() {
            CartResponse response = new CartResponse();
            response.setCartId(7L);
            response.setUserId(42L);
            response.setItems(List.of());
            response.setTotalAmount(new BigDecimal("8999.00"));
            response.setTotalItems(3);
            response.setTotalUniqueItems(1);
            response.setEnriched(true);
            response.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            response.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
            return response;
        }

        @Test
        @DisplayName("every accessor round trips")
        void accessorsRoundTrip() {
            CartResponse response = cartResponse();

            assertThat(response.getCartId()).isEqualTo(7L);
            assertThat(response.getUserId()).isEqualTo(42L);
            assertThat(response.getItems()).isEmpty();
            assertThat(response.getTotalAmount()).isEqualByComparingTo("8999.00");
            assertThat(response.getTotalItems()).isEqualTo(3);
            assertThat(response.getTotalUniqueItems()).isEqualTo(1);
            assertThat(response.isEnriched()).isTrue();
            assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
            assertThat(response.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
        }

        @Test
        @DisplayName("a brand new instance is entirely empty")
        void defaultsAreEmpty() {
            CartResponse response = new CartResponse();

            assertThat(response.getCartId()).isNull();
            assertThat(response.getItems()).isNull();
            assertThat(response.getTotalAmount()).isNull();
            assertThat(response.isEnriched()).isFalse();
        }

        @Test
        @DisplayName("serialises every field of the contract")
        void jsonShape() throws Exception {
            String json = objectMapper.writeValueAsString(cartResponse());

            assertThat(json).contains(
                    "\"cartId\":7",
                    "\"userId\":42",
                    "\"items\":[]",
                    "\"totalAmount\":8999.00",
                    "\"totalItems\":3",
                    "\"totalUniqueItems\":1",
                    "\"enriched\":true",
                    "\"createdAt\"",
                    "\"updatedAt\"");
        }

        @Test
        @DisplayName("the boolean is published as 'enriched', not as 'isEnriched'")
        void enrichedPropertyName() throws Exception {
            assertThat(objectMapper.writeValueAsString(cartResponse()))
                    .contains("\"enriched\"")
                    .doesNotContain("\"isEnriched\"");
        }

        @Test
        @DisplayName("the total keeps its monetary scale through JSON")
        void keepsTheMonetaryScale() throws Exception {
            CartResponse response = new CartResponse();
            response.setTotalAmount(new BigDecimal("10.50"));

            assertThat(objectMapper.writeValueAsString(response)).contains("\"totalAmount\":10.50");
        }

        @Test
        @DisplayName("equals / hashCode come from @Data")
        void valueSemantics() {
            assertThat(cartResponse()).isEqualTo(cartResponse()).hasSameHashCodeAs(cartResponse());
        }
    }

    @Nested
    @DisplayName("CartItemResponse")
    class Item {

        private CartItemResponse itemResponse() {
            CartItemResponse response = new CartItemResponse();
            response.setId(500L);
            response.setProductId(1L);
            response.setVariantId(2L);
            response.setQuantity(3);
            response.setPriceAtAdd(new BigDecimal("8999.00"));
            response.setItemTotal(new BigDecimal("26997.00"));
            response.setProductDetails(new ProductDetails());
            return response;
        }

        @Test
        @DisplayName("every accessor round trips")
        void accessorsRoundTrip() {
            CartItemResponse response = itemResponse();

            assertThat(response.getId()).isEqualTo(500L);
            assertThat(response.getProductId()).isEqualTo(1L);
            assertThat(response.getVariantId()).isEqualTo(2L);
            assertThat(response.getQuantity()).isEqualTo(3);
            assertThat(response.getPriceAtAdd()).isEqualByComparingTo("8999.00");
            assertThat(response.getItemTotal()).isEqualByComparingTo("26997.00");
            assertThat(response.getProductDetails()).isNotNull();
        }

        @Test
        @DisplayName("serialises every field of the contract")
        void jsonShape() throws Exception {
            String json = objectMapper.writeValueAsString(itemResponse());

            assertThat(json).contains(
                    "\"id\":500",
                    "\"productId\":1",
                    "\"variantId\":2",
                    "\"quantity\":3",
                    "\"priceAtAdd\":8999.00",
                    "\"itemTotal\":26997.00",
                    "\"productDetails\"");
        }

        @Test
        @DisplayName("a null productDetails is still serialised - it signals a failed enrichment")
        void aNullProductDetailsIsSerialised() throws Exception {
            CartItemResponse response = new CartItemResponse();

            assertThat(objectMapper.writeValueAsString(response)).contains("\"productDetails\":null");
        }
    }

    @Nested
    @DisplayName("ProductDetails")
    class Details {

        @Test
        @DisplayName("every accessor round trips")
        void accessorsRoundTrip() {
            ProductDetails details = new ProductDetails();
            details.setProductName("JBL Flip 6");
            details.setProductBrand("JBL");
            details.setProductImageUrl("products/1/primary.png");
            details.setVariantName("M - Black");
            details.setSize("M");
            details.setColor("Black");
            details.setVariantSku("SKU-1");

            assertThat(details.getProductName()).isEqualTo("JBL Flip 6");
            assertThat(details.getProductBrand()).isEqualTo("JBL");
            assertThat(details.getProductImageUrl()).isEqualTo("products/1/primary.png");
            assertThat(details.getVariantName()).isEqualTo("M - Black");
            assertThat(details.getSize()).isEqualTo("M");
            assertThat(details.getColor()).isEqualTo("Black");
            assertThat(details.getVariantSku()).isEqualTo("SKU-1");
        }

        @Test
        @DisplayName("serialises every field of the contract")
        void jsonShape() throws Exception {
            ProductDetails details = new ProductDetails();
            details.setProductName("JBL Flip 6");
            details.setVariantName("M - Black");

            String json = objectMapper.writeValueAsString(details);

            assertThat(json).contains(
                    "\"productName\":\"JBL Flip 6\"",
                    "\"productBrand\"",
                    "\"productImageUrl\"",
                    "\"variantName\":\"M - Black\"",
                    "\"size\"",
                    "\"color\"",
                    "\"variantSku\"");
        }
    }

    @Nested
    @DisplayName("ProductCartResponse (the product-service payload)")
    class ProductPayload {

        @Test
        @DisplayName("every accessor round trips")
        void accessorsRoundTrip() {
            ProductCartResponse response = new ProductCartResponse();
            response.setProductId(1L);
            response.setProductName("JBL Flip 6");
            response.setBrandName("JBL");
            response.setPrice(new BigDecimal("8999.00"));
            response.setIsAvailable(true);
            response.setVariantId(2L);
            response.setSize("M");
            response.setColor("Black");
            response.setInStock(true);
            response.setImageUrl("products/1/primary.png");

            assertThat(response.getProductId()).isEqualTo(1L);
            assertThat(response.getProductName()).isEqualTo("JBL Flip 6");
            assertThat(response.getBrandName()).isEqualTo("JBL");
            assertThat(response.getPrice()).isEqualByComparingTo("8999.00");
            assertThat(response.getIsAvailable()).isTrue();
            assertThat(response.getVariantId()).isEqualTo(2L);
            assertThat(response.getSize()).isEqualTo("M");
            assertThat(response.getColor()).isEqualTo("Black");
            assertThat(response.getInStock()).isTrue();
            assertThat(response.getImageUrl()).isEqualTo("products/1/primary.png");
        }

        @Test
        @DisplayName("the flags are boxed, so an absent field deserialises to null")
        void theFlagsAreNullable() throws Exception {
            ProductCartResponse response = objectMapper.readValue(
                    "{\"productId\":1,\"variantId\":2}", ProductCartResponse.class);

            assertThat(response.getIsAvailable()).isNull();
            assertThat(response.getInStock()).isNull();
        }

        @Test
        @DisplayName("deserialises the payload product-service actually sends")
        void deserialisesTheProductServicePayload() throws Exception {
            String json = """
                    {
                      "productId": 1,
                      "productName": "JBL Flip 6",
                      "brandName": "JBL",
                      "price": 8999.00,
                      "isAvailable": true,
                      "variantId": 2,
                      "size": "M",
                      "color": "Black",
                      "inStock": true,
                      "imageUrl": "products/1/primary.png"
                    }
                    """;

            ProductCartResponse response = objectMapper.readValue(json, ProductCartResponse.class);

            assertThat(response.getProductName()).isEqualTo("JBL Flip 6");
            assertThat(response.getPrice()).isEqualByComparingTo("8999.00");
            assertThat(response.getIsAvailable()).isTrue();
            assertThat(response.getInStock()).isTrue();
            assertThat(response.getImageUrl()).isEqualTo("products/1/primary.png");
        }
    }
}


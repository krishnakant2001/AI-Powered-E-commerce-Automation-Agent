package com.strikerkk.aicommerce.order_service.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.AddressResponse;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.CartItemResponse;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductDetails;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.order_service.dto.request.PlaceOrderRequest;
import com.strikerkk.aicommerce.order_service.dto.request.UpdateOrderStatusRequest;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The incoming payloads")
class RequestAndClientPayloadTest {

    /** Configured like the Boot mapper Feign and MVC share: unknown fields never break a call. */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    // ------------------------------------------------------------------
    // requests
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("PlaceOrderRequest")
    class PlaceOrder {

        @Test
        @DisplayName("is deserialized from the body of the checkout")
        void isDeserializedFromTheBody() throws Exception {
            PlaceOrderRequest request = objectMapper.readValue(
                    "{\"userId\":42,\"addressId\":7,\"productId\":1,\"variantId\":2}",
                    PlaceOrderRequest.class);

            assertThat(request.getUserId()).isEqualTo(42L);
            assertThat(request.getAddressId()).isEqualTo(7L);
            assertThat(request.getProductId()).isEqualTo(1L);
            assertThat(request.getVariantId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("leaves the product out for a cart order - only buy now needs it")
        void leavesTheProductOutForACartOrder() throws Exception {
            PlaceOrderRequest request = objectMapper.readValue(
                    "{\"userId\":42,\"addressId\":7}", PlaceOrderRequest.class);

            assertThat(request.getProductId()).isNull();
            assertThat(request.getVariantId()).isNull();
        }

        @Test
        @DisplayName("carries no validation annotations today - the service guards instead")
        void carriesNoValidationAnnotations() {
            assertThat(java.util.Arrays.stream(PlaceOrderRequest.class.getDeclaredFields())
                    .flatMap(field -> java.util.Arrays.stream(field.getAnnotations()))
                    .anyMatch(annotation -> annotation.annotationType().getPackageName()
                            .startsWith("jakarta.validation")))
                    .isFalse();
        }

        @Test
        @DisplayName("is a value object - equal content means equal request")
        void isAValueObject() {
            assertThat(TestDataFactory.placeOrderRequest())
                    .isEqualTo(TestDataFactory.placeOrderRequest())
                    .hasSameHashCodeAs(TestDataFactory.placeOrderRequest());
            assertThat(TestDataFactory.placeOrderRequest().toString()).contains("userId");
        }

        @Test
        @DisplayName("ignores a field the gateway added")
        void ignoresAnUnknownField() throws Exception {
            PlaceOrderRequest request = objectMapper.readValue(
                    "{\"userId\":42,\"addressId\":7,\"coupon\":\"FREE\"}", PlaceOrderRequest.class);

            assertThat(request.getUserId()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("UpdateOrderStatusRequest")
    class UpdateStatus {

        @Test
        @DisplayName("reads the new status by its name")
        void readsTheNewStatus() throws Exception {
            UpdateOrderStatusRequest request =
                    objectMapper.readValue("{\"status\":\"SHIPPED\"}", UpdateOrderStatusRequest.class);

            assertThat(request.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("stays empty when no status is sent")
        void staysEmptyWithoutAStatus() throws Exception {
            assertThat(objectMapper.readValue("{}", UpdateOrderStatusRequest.class).getStatus()).isNull();
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            UpdateOrderStatusRequest first = new UpdateOrderStatusRequest();
            first.setStatus(OrderStatus.DELIVERED);
            UpdateOrderStatusRequest second = new UpdateOrderStatusRequest();
            second.setStatus(OrderStatus.DELIVERED);

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }
    }

    // ------------------------------------------------------------------
    // what the other services answer
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("CartItemResponse")
    class CartItem {

        private static final String JSON = """
                {
                  "id": 500,
                  "productId": 1,
                  "variantId": 2,
                  "quantity": 3,
                  "priceAtAdd": 8999.00,
                  "itemTotal": 26997.00,
                  "productDetails": {
                    "productName": "JBL Flip 6",
                    "productBrand": "JBL",
                    "productImageUrl": "products/1/primary.png",
                    "variantName": "M - Black",
                    "size": "M",
                    "color": "Black",
                    "variantSku": "JBL-FLIP6-M-BLK"
                  }
                }
                """;

        @Test
        @DisplayName("is read back from what cart-service answers, nested product included")
        void isReadBackFromTheCartAnswer() throws Exception {
            CartItemResponse item = objectMapper.readValue(JSON, CartItemResponse.class);

            assertThat(item.getId()).isEqualTo(500L);
            assertThat(item.getProductId()).isEqualTo(1L);
            assertThat(item.getVariantId()).isEqualTo(2L);
            assertThat(item.getQuantity()).isEqualTo(3);
            assertThat(item.getPriceAtAdd()).isEqualByComparingTo("8999.00");
            assertThat(item.getItemTotal()).isEqualByComparingTo("26997.00");

            ProductDetails details = item.getProductDetails();
            assertThat(details).isNotNull();
            assertThat(details.getProductName()).isEqualTo("JBL Flip 6");
            assertThat(details.getProductBrand()).isEqualTo("JBL");
            assertThat(details.getProductImageUrl()).isEqualTo("products/1/primary.png");
            assertThat(details.getVariantName()).isEqualTo("M - Black");
            assertThat(details.getSize()).isEqualTo("M");
            assertThat(details.getColor()).isEqualTo("Black");
            assertThat(details.getVariantSku()).isEqualTo("JBL-FLIP6-M-BLK");
        }

        @Test
        @DisplayName("keeps the price exact - money is a BigDecimal, never a double")
        void keepsThePriceExact() throws Exception {
            assertThat(objectMapper.readValue(JSON, CartItemResponse.class).getPriceAtAdd())
                    .isInstanceOf(BigDecimal.class)
                    .isEqualByComparingTo(new BigDecimal("8999.00"));
        }

        @Test
        @DisplayName("survives an answer without the nested product")
        void survivesAMissingProduct() throws Exception {
            CartItemResponse item = objectMapper.readValue(
                    "{\"id\":1,\"productId\":1,\"variantId\":2,\"quantity\":1,\"priceAtAdd\":10.00}",
                    CartItemResponse.class);

            assertThat(item.getProductDetails()).isNull();
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            assertThat(TestDataFactory.cartItemResponse())
                    .isEqualTo(TestDataFactory.cartItemResponse())
                    .hasSameHashCodeAs(TestDataFactory.cartItemResponse());
        }
    }

    @Nested
    @DisplayName("ProductItemResponse")
    class ProductItem {

        @Test
        @DisplayName("is read back from what product-service answers")
        void isReadBackFromTheProductAnswer() throws Exception {
            ProductItemResponse item = objectMapper.readValue("""
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
                    """, ProductItemResponse.class);

            assertThat(item.getProductId()).isEqualTo(1L);
            assertThat(item.getProductName()).isEqualTo("JBL Flip 6");
            assertThat(item.getBrandName()).isEqualTo("JBL");
            assertThat(item.getPrice()).isEqualByComparingTo("8999.00");
            assertThat(item.getIsAvailable()).isTrue();
            assertThat(item.getVariantId()).isEqualTo(2L);
            assertThat(item.getSize()).isEqualTo("M");
            assertThat(item.getColor()).isEqualTo("Black");
            assertThat(item.getInStock()).isTrue();
            assertThat(item.getImageUrl()).isEqualTo("products/1/primary.png");
        }

        @Test
        @DisplayName("keeps the availability flags as objects, so 'unknown' stays null")
        void keepsTheFlagsNullable() throws Exception {
            ProductItemResponse item = objectMapper.readValue("{\"productId\":1}", ProductItemResponse.class);

            assertThat(item.getIsAvailable()).isNull();
            assertThat(item.getInStock()).isNull();
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            assertThat(TestDataFactory.productItemResponse())
                    .isEqualTo(TestDataFactory.productItemResponse())
                    .hasSameHashCodeAs(TestDataFactory.productItemResponse());
        }
    }

    @Nested
    @DisplayName("AddressResponse")
    class Address {

        @Test
        @DisplayName("is read back from what user-service answers")
        void isReadBackFromTheUserAnswer() throws Exception {
            AddressResponse address = objectMapper.readValue("""
                    {
                      "houseNo": "12B",
                      "street": "MG Road",
                      "city": "Bengaluru",
                      "state": "Karnataka",
                      "country": "India",
                      "pinCode": "560001",
                      "isDefault": true
                    }
                    """, AddressResponse.class);

            assertThat(address.getHouseNo()).isEqualTo("12B");
            assertThat(address.getStreet()).isEqualTo("MG Road");
            assertThat(address.getCity()).isEqualTo("Bengaluru");
            assertThat(address.getState()).isEqualTo("Karnataka");
            assertThat(address.getCountry()).isEqualTo("India");
            assertThat(address.getPinCode()).isEqualTo("560001");
            assertThat(address.getIsDefault()).isTrue();
        }

        @Test
        @DisplayName("keeps the pin code as text, so a leading zero survives")
        void keepsThePinCodeAsText() throws Exception {
            AddressResponse address =
                    objectMapper.readValue("{\"pinCode\":\"012345\"}", AddressResponse.class);

            assertThat(address.getPinCode()).isEqualTo("012345");
        }

        @Test
        @DisplayName("flattens into the single line the order carries")
        void flattensIntoASingleLine() {
            AddressResponse address = TestDataFactory.addressResponse();

            String flattened = address.getHouseNo() + " " + address.getStreet() + " "
                    + address.getCity() + " " + address.getState() + " "
                    + address.getCountry() + " " + address.getPinCode();

            assertThat(flattened).isEqualTo(TestDataFactory.FORMATTED_ADDRESS);
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            assertThat(TestDataFactory.addressResponse())
                    .isEqualTo(TestDataFactory.addressResponse())
                    .hasSameHashCodeAs(TestDataFactory.addressResponse());
        }
    }
}


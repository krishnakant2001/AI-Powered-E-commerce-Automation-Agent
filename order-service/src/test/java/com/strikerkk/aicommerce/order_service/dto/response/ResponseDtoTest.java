package com.strikerkk.aicommerce.order_service.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The outgoing payloads")
class ResponseDtoTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private OrderItemResponse itemResponse() {
        OrderItemResponse item = new OrderItemResponse();
        item.setId(TestDataFactory.ORDER_ITEM_ID);
        item.setProductId(TestDataFactory.PRODUCT_ID);
        item.setVariantId(TestDataFactory.VARIANT_ID);
        item.setProductName(TestDataFactory.PRODUCT_NAME);
        item.setProductBrand(TestDataFactory.BRAND);
        item.setProductImageUrl(TestDataFactory.IMAGE_URL);
        item.setSize(TestDataFactory.SIZE);
        item.setColor(TestDataFactory.COLOR);
        item.setQuantity(2);
        item.setPriceAtOrder(TestDataFactory.PRICE);
        item.setLineTotal(TestDataFactory.PRICE.multiply(BigDecimal.valueOf(2)));
        return item;
    }

    private OrderResponse orderResponse() {
        OrderResponse response = new OrderResponse();
        response.setId(TestDataFactory.ORDER_ID);
        response.setUserId(TestDataFactory.USER_ID);
        response.setAddressId(TestDataFactory.ADDRESS_ID);
        response.setAddress(TestDataFactory.FORMATTED_ADDRESS);
        response.setTotalAmount(new BigDecimal("17998.00"));
        response.setDeliveryCharges(BigDecimal.ZERO);
        response.setNeedToPay(new BigDecimal("17998.00"));
        response.setStatus(OrderStatus.PENDING);
        response.setOrderItems(List.of(itemResponse()));
        response.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        response.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 5));
        return response;
    }

    // ------------------------------------------------------------------
    // OrderResponse
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("OrderResponse")
    class Order {

        @Test
        @DisplayName("carries everything the checkout screen needs")
        void carriesEverythingTheScreenNeeds() {
            OrderResponse response = orderResponse();

            assertThat(response.getId()).isEqualTo(TestDataFactory.ORDER_ID);
            assertThat(response.getUserId()).isEqualTo(TestDataFactory.USER_ID);
            assertThat(response.getAddressId()).isEqualTo(TestDataFactory.ADDRESS_ID);
            assertThat(response.getAddress()).isEqualTo(TestDataFactory.FORMATTED_ADDRESS);
            assertThat(response.getTotalAmount()).isEqualByComparingTo("17998.00");
            assertThat(response.getDeliveryCharges()).isEqualByComparingTo("0");
            assertThat(response.getNeedToPay()).isEqualByComparingTo("17998.00");
            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.getOrderItems()).hasSize(1);
            assertThat(response.getCreatedAt()).isNotNull();
            assertThat(response.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("serializes the status as its readable name")
        void serializesTheStatusAsItsName() throws Exception {
            assertThat(objectMapper.writeValueAsString(orderResponse()))
                    .contains("\"status\":\"PENDING\"");
        }

        @Test
        @DisplayName("serializes the amounts without losing a paisa")
        void serializesTheAmountsExactly() throws Exception {
            assertThat(objectMapper.writeValueAsString(orderResponse()))
                    .contains("\"totalAmount\":17998.00")
                    .contains("\"needToPay\":17998.00");
        }

        @Test
        @DisplayName("serializes both timestamps as ISO text")
        void serializesTheTimestamps() throws Exception {
            assertThat(objectMapper.writeValueAsString(orderResponse()))
                    .contains("\"createdAt\":\"2026-01-01T10:00:00\"")
                    .contains("\"updatedAt\":\"2026-01-01T10:05:00\"");
        }

        @Test
        @DisplayName("serializes the flat address, never the address object")
        void serializesTheFlatAddress() throws Exception {
            assertThat(objectMapper.writeValueAsString(orderResponse()))
                    .contains("\"address\":\"" + TestDataFactory.FORMATTED_ADDRESS + "\"")
                    .contains("\"addressId\":" + TestDataFactory.ADDRESS_ID);
        }

        @Test
        @DisplayName("keeps an empty line list empty, it never turns into null")
        void keepsAnEmptyLineListEmpty() throws Exception {
            OrderResponse response = orderResponse();
            response.setOrderItems(List.of());

            assertThat(objectMapper.writeValueAsString(response)).contains("\"orderItems\":[]");
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            assertThat(orderResponse()).isEqualTo(orderResponse()).hasSameHashCodeAs(orderResponse());
            assertThat(orderResponse().toString()).contains("PENDING");
        }
    }

    // ------------------------------------------------------------------
    // OrderItemResponse
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("OrderItemResponse")
    class Item {

        @Test
        @DisplayName("carries the snapshot of the product as it was bought")
        void carriesTheSnapshot() {
            OrderItemResponse item = itemResponse();

            assertThat(item.getId()).isEqualTo(TestDataFactory.ORDER_ITEM_ID);
            assertThat(item.getProductId()).isEqualTo(TestDataFactory.PRODUCT_ID);
            assertThat(item.getVariantId()).isEqualTo(TestDataFactory.VARIANT_ID);
            assertThat(item.getProductName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(item.getProductBrand()).isEqualTo(TestDataFactory.BRAND);
            assertThat(item.getProductImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
            assertThat(item.getSize()).isEqualTo(TestDataFactory.SIZE);
            assertThat(item.getColor()).isEqualTo(TestDataFactory.COLOR);
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getPriceAtOrder()).isEqualByComparingTo(TestDataFactory.PRICE);
            assertThat(item.getLineTotal()).isEqualByComparingTo("17998.00");
        }

        @Test
        @DisplayName("serializes the unit price and the line total apart")
        void serializesBothAmounts() throws Exception {
            assertThat(objectMapper.writeValueAsString(itemResponse()))
                    .contains("\"priceAtOrder\":8999.00")
                    .contains("\"lineTotal\":17998.00");
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            assertThat(itemResponse()).isEqualTo(itemResponse()).hasSameHashCodeAs(itemResponse());
        }
    }

    // ------------------------------------------------------------------
    // OrderSummaryResponse
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("OrderSummaryResponse")
    class Summary {

        private OrderSummaryResponse summary() {
            OrderSummaryResponse summary = new OrderSummaryResponse();
            summary.setId(TestDataFactory.ORDER_ID);
            summary.setFirstItemName(TestDataFactory.PRODUCT_NAME);
            summary.setFirstItemImageUrl(TestDataFactory.IMAGE_URL);
            summary.setTotalItems(2);
            summary.setTotalAmount(new BigDecimal("17998.00"));
            summary.setStatus(OrderStatus.CONFIRMED);
            summary.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            return summary;
        }

        @Test
        @DisplayName("carries just enough for a card in the order history")
        void carriesJustEnoughForACard() {
            OrderSummaryResponse summary = summary();

            assertThat(summary.getId()).isEqualTo(TestDataFactory.ORDER_ID);
            assertThat(summary.getFirstItemName()).isEqualTo(TestDataFactory.PRODUCT_NAME);
            assertThat(summary.getFirstItemImageUrl()).isEqualTo(TestDataFactory.IMAGE_URL);
            assertThat(summary.getTotalItems()).isEqualTo(2);
            assertThat(summary.getTotalAmount()).isEqualByComparingTo("17998.00");
            assertThat(summary.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(summary.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("never carries the lines themselves - that is the detail call")
        void neverCarriesTheLines() {
            assertThat(OrderSummaryResponse.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("orderItems");
        }

        @Test
        @DisplayName("serializes the thumbnail of the first line")
        void serializesTheThumbnail() throws Exception {
            assertThat(objectMapper.writeValueAsString(summary()))
                    .contains("\"firstItemName\":\"" + TestDataFactory.PRODUCT_NAME + "\"")
                    .contains("\"firstItemImageUrl\":\"" + TestDataFactory.IMAGE_URL + "\"")
                    .contains("\"totalItems\":2");
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            assertThat(summary()).isEqualTo(summary()).hasSameHashCodeAs(summary());
        }
    }
}



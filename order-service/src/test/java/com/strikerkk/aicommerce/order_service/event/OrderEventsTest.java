package com.strikerkk.aicommerce.order_service.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.strikerkk.aicommerce.payment_service.event.PaymentSuccessEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("The Kafka events")
class OrderEventsTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    // ------------------------------------------------------------------
    // outgoing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("OrderConfirmedEvent")
    class Confirmed {

        private OrderConfirmedEvent event() {
            return OrderConfirmedEvent.builder()
                    .orderId(100L)
                    .orderPlacedItems(List.of(
                            OrderPlacedItem.builder().id(500L).productId(1L).variantId(2L).quantity(3).build()))
                    .build();
        }

        @Test
        @DisplayName("carries the order and every line that has to be taken out of the stock")
        void carriesTheOrderAndItsLines() {
            OrderConfirmedEvent event = event();

            assertThat(event.getOrderId()).isEqualTo(100L);
            assertThat(event.getOrderPlacedItems()).hasSize(1);
            assertThat(event.getOrderPlacedItems().getFirst().getProductId()).isEqualTo(1L);
            assertThat(event.getOrderPlacedItems().getFirst().getVariantId()).isEqualTo(2L);
            assertThat(event.getOrderPlacedItems().getFirst().getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("is serialized as JSON, exactly as the producer is configured")
        void isSerializedAsJson() throws Exception {
            String json = objectMapper.writeValueAsString(event());

            assertThat(json)
                    .contains("\"orderId\":100")
                    .contains("\"orderPlacedItems\"")
                    .contains("\"quantity\":3");
        }

        @Test
        @DisplayName("is produced only - reading it back needs the no args constructor it does not have")
        void isProducedOnly() throws Exception {
            String json = objectMapper.writeValueAsString(event());

            // product-service owns its own copy of this payload, this service only writes it.
            assertThatThrownBy(() -> objectMapper.readValue(json, OrderConfirmedEvent.class))
                    .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidDefinitionException.class);
        }

        @Test
        @DisplayName("tolerates an order without lines")
        void toleratesAnOrderWithoutLines() throws Exception {
            OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                    .orderId(100L)
                    .orderPlacedItems(List.of())
                    .build();

            assertThat(objectMapper.writeValueAsString(event)).contains("\"orderPlacedItems\":[]");
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            assertThat(event()).isEqualTo(event()).hasSameHashCodeAs(event());
            assertThat(event().toString()).contains("orderId");
        }
    }

    @Nested
    @DisplayName("OrderPlacedItem")
    class PlacedItem {

        @Test
        @DisplayName("carries the ids and the quantity, nothing the stock does not need")
        void carriesOnlyWhatTheStockNeeds() {
            assertThat(OrderPlacedItem.class.getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .containsExactlyInAnyOrder("id", "productId", "variantId", "quantity");
        }

        @Test
        @DisplayName("is serialized with every id the receiver needs")
        void isSerializedWithEveryId() throws Exception {
            OrderPlacedItem item =
                    OrderPlacedItem.builder().id(1L).productId(2L).variantId(3L).quantity(4).build();

            assertThat(objectMapper.writeValueAsString(item))
                    .contains("\"id\":1")
                    .contains("\"productId\":2")
                    .contains("\"variantId\":3")
                    .contains("\"quantity\":4");
        }

        @Test
        @DisplayName("is built through its builder, which is how the consumer maps a line")
        void isBuiltThroughItsBuilder() {
            OrderPlacedItem item =
                    OrderPlacedItem.builder().id(1L).productId(2L).variantId(3L).quantity(4).build();

            assertThat(item)
                    .isEqualTo(OrderPlacedItem.builder().id(1L).productId(2L).variantId(3L).quantity(4).build());
            assertThat(item.getQuantity()).isEqualTo(4);
        }
    }

    // ------------------------------------------------------------------
    // incoming
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("PaymentSuccessEvent")
    class PaymentSuccess {

        @Test
        @DisplayName("is read back from what payment-service publishes")
        void isReadBackFromThePaymentAnswer() throws Exception {
            PaymentSuccessEvent event = objectMapper.readValue(
                    "{\"id\":1,\"userId\":42,\"orderId\":100,\"paymentStatus\":\"SUCCESS\"}",
                    PaymentSuccessEvent.class);

            assertThat(event.getId()).isEqualTo(1L);
            assertThat(event.getUserId()).isEqualTo(42L);
            assertThat(event.getOrderId()).isEqualTo(100L);
            assertThat(event.getPaymentStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("keeps the payment status as free text, the consumer compares it")
        void keepsTheStatusAsText() throws Exception {
            assertThat(objectMapper.readValue(
                    "{\"orderId\":1,\"paymentStatus\":\"FAILED\"}", PaymentSuccessEvent.class)
                    .getPaymentStatus())
                    .isEqualTo("FAILED");
        }

        @Test
        @DisplayName("tolerates a field this service does not care about")
        void toleratesAnUnknownField() throws Exception {
            PaymentSuccessEvent event = objectMapper.readValue(
                    "{\"orderId\":100,\"paymentStatus\":\"SUCCESS\",\"razorpayId\":\"pay_123\"}",
                    PaymentSuccessEvent.class);

            assertThat(event.getOrderId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("lives in the shared payment_service package the trusted packages cover")
        void livesInTheTrustedPackage() {
            assertThat(PaymentSuccessEvent.class.getPackageName())
                    .isEqualTo("com.strikerkk.aicommerce.payment_service.event")
                    .startsWith("com.strikerkk.aicommerce");
        }

        @Test
        @DisplayName("is a value object")
        void isAValueObject() {
            PaymentSuccessEvent first = new PaymentSuccessEvent();
            first.setOrderId(100L);
            first.setPaymentStatus("SUCCESS");
            PaymentSuccessEvent second = new PaymentSuccessEvent();
            second.setOrderId(100L);
            second.setPaymentStatus("SUCCESS");

            assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        }
    }
}





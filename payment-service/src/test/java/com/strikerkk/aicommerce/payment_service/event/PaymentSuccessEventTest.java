package com.strikerkk.aicommerce.payment_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikerkk.aicommerce.payment_service.entity.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentSuccessEvent")
class PaymentSuccessEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("carries the payment, the customer, the order and the status")
    void carriesTheFourFields() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .id(500L)
                .userId(42L)
                .orderId(100L)
                .paymentStatus(PaymentStatus.SUCCESS.toString())
                .build();

        assertThat(event.getId()).isEqualTo(500L);
        assertThat(event.getUserId()).isEqualTo(42L);
        assertThat(event.getOrderId()).isEqualTo(100L);
        assertThat(event.getPaymentStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("declares exactly those four fields - the contract order-service relies on")
    void declaresExactlyFourFields() {
        assertThat(PaymentSuccessEvent.class.getDeclaredFields())
                .filteredOn(f -> !f.isSynthetic())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("id", "userId", "orderId", "paymentStatus");
    }

    @Test
    @DisplayName("carries the status as text, so both services stay independent of each other's enum")
    void carriesTheStatusAsText() throws Exception {
        assertThat(PaymentSuccessEvent.class.getDeclaredField("paymentStatus").getType())
                .isEqualTo(String.class);
    }

    @Test
    @DisplayName("serialises to the JSON the Kafka producer sends")
    void serialisesToJson() throws Exception {
        String json = objectMapper.writeValueAsString(PaymentSuccessEvent.builder()
                .id(500L).userId(42L).orderId(100L).paymentStatus("SUCCESS").build());

        assertThat(json).contains("\"id\":500")
                .contains("\"userId\":42")
                .contains("\"orderId\":100")
                .contains("\"paymentStatus\":\"SUCCESS\"");
    }

    @Test
    @DisplayName("is produce only: it cannot be read back without a no argument constructor")
    void isProduceOnly() throws Exception {
        String json = objectMapper.writeValueAsString(PaymentSuccessEvent.builder()
                .id(500L).userId(42L).orderId(100L).paymentStatus("SUCCESS").build());

        // @Data + @Builder leaves only the all args constructor, so Jackson has no creator.
        // payment-service only ever publishes this event; order-service declares its own
        // deserialisable copy of the class, which is why the pipeline still works.
        assertThatThrownBy(() -> objectMapper.readValue(json, PaymentSuccessEvent.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidDefinitionException.class)
                .hasMessageContaining("no Creators");
    }

    @Test
    @DisplayName("never carries the amount or any gateway id - the order already knows them")
    void neverCarriesTheMoney() {
        assertThat(PaymentSuccessEvent.class.getDeclaredFields())
                .filteredOn(f -> !f.isSynthetic())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("amount", "gatewayOrderId", "gatewayPaymentId", "gatewaySignature");
    }

    @Test
    @DisplayName("two events about the same payment are equal, a redelivery is detectable")
    void twoEqualEventsAreEqual() {
        PaymentSuccessEvent first = PaymentSuccessEvent.builder()
                .id(500L).userId(42L).orderId(100L).paymentStatus("SUCCESS").build();
        PaymentSuccessEvent second = PaymentSuccessEvent.builder()
                .id(500L).userId(42L).orderId(100L).paymentStatus("SUCCESS").build();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("an unset field stays null rather than defaulting to zero")
    void anUnsetFieldStaysNull() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder().orderId(100L).build();

        assertThat(event.getId()).isNull();
        assertThat(event.getUserId()).isNull();
        assertThat(event.getPaymentStatus()).isNull();
    }
}




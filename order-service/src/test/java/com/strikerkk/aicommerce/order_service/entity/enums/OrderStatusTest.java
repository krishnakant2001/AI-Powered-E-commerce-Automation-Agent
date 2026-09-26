package com.strikerkk.aicommerce.order_service.entity.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderStatus")
class OrderStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("covers the whole life cycle of an order, in order")
    void coversTheWholeLifeCycle() {
        assertThat(OrderStatus.values()).containsExactly(
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED,
                OrderStatus.SHIPPED,
                OrderStatus.DELIVERED,
                OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("has exactly five states - a new one needs a conscious decision")
    void hasExactlyFiveStates() {
        assertThat(OrderStatus.values()).hasSize(5);
    }

    @ParameterizedTest(name = "[{index}] {0} survives a round trip through its name")
    @EnumSource(OrderStatus.class)
    @DisplayName("every state round trips through valueOf, which is how it is stored")
    void everyStateRoundTrips(OrderStatus status) {
        assertThat(OrderStatus.valueOf(status.name())).isSameAs(status);
    }

    @ParameterizedTest(name = "[{index}] {0} round trips through JSON")
    @EnumSource(OrderStatus.class)
    @DisplayName("every state round trips through JSON, which is how it is answered")
    void everyStateRoundTripsThroughJson(OrderStatus status) throws Exception {
        String json = objectMapper.writeValueAsString(status);

        assertThat(json).isEqualTo("\"" + status.name() + "\"");
        assertThat(objectMapper.readValue(json, OrderStatus.class)).isSameAs(status);
    }

    @Test
    @DisplayName("a freshly placed order is the first state")
    void aFreshOrderIsTheFirstState() {
        assertThat(OrderStatus.values()[0]).isEqualTo(OrderStatus.PENDING);
        assertThat(OrderStatus.PENDING.ordinal()).isZero();
    }

    @Test
    @DisplayName("an unknown name is rejected")
    void anUnknownNameIsRejected() {
        assertThatThrownBy(() -> OrderStatus.valueOf("PAID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("is written in upper case - the column stores the name as text")
    void isWrittenInUpperCase() {
        assertThat(OrderStatus.values())
                .allSatisfy(status -> assertThat(status.name()).isEqualTo(status.name().toUpperCase()));
    }
}


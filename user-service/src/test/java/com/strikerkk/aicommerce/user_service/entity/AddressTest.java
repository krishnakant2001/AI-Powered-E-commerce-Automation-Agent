package com.strikerkk.aicommerce.user_service.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Address entity")
class AddressTest {

    private static Address newAddress() {
        return Address.builder()
                .street("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .country("India")
                .pinCode("560001")
                .build();
    }

    @Test
    @DisplayName("is not the default address unless explicitly requested")
    void shouldDefaultIsDefaultToFalse() {
        assertThat(newAddress().getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("allows the default flag to be set through the builder")
    void shouldAllowIsDefaultToBeSet() {
        Address address = Address.builder()
                .street("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .country("India")
                .pinCode("560001")
                .isDefault(true)
                .build();

        assertThat(address.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("exposes every attribute through its setters")
    void shouldExposeSetters() {
        User user = User.builder()
                .firstName("Krishnakant")
                .email("krishna@example.com")
                .password("secret")
                .build();
        Address address = newAddress();

        address.setId(3L);
        address.setUser(user);
        address.setHouseNo("B-101");
        address.setStreet("Brigade Road");
        address.setCity("Mysuru");
        address.setState("Karnataka");
        address.setCountry("India");
        address.setPinCode("570001");
        address.setIsDefault(true);

        assertThat(address.getId()).isEqualTo(3L);
        assertThat(address.getUser()).isSameAs(user);
        assertThat(address.getHouseNo()).isEqualTo("B-101");
        assertThat(address.getStreet()).isEqualTo("Brigade Road");
        assertThat(address.getCity()).isEqualTo("Mysuru");
        assertThat(address.getState()).isEqualTo("Karnataka");
        assertThat(address.getCountry()).isEqualTo("India");
        assertThat(address.getPinCode()).isEqualTo("570001");
        assertThat(address.getIsDefault()).isTrue();
    }
}


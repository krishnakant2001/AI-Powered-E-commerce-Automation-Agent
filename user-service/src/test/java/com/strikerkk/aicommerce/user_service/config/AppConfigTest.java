package com.strikerkk.aicommerce.user_service.config;

import com.strikerkk.aicommerce.user_service.dto.response.AddressResponse;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.entity.Address;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.entity.enums.Role;
import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppConfig beans")
class AppConfigTest {

    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig();
    }

    @Test
    @DisplayName("exposes a BCrypt password encoder")
    void shouldExposeBCryptPasswordEncoder() {
        PasswordEncoder passwordEncoder = appConfig.passwordEncoder();

        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    @DisplayName("the password encoder produces a salted, verifiable hash")
    void shouldProduceSaltedVerifiableHash() {
        PasswordEncoder passwordEncoder = appConfig.passwordEncoder();

        String firstHash = passwordEncoder.encode(TestDataFactory.RAW_PASSWORD);
        String secondHash = passwordEncoder.encode(TestDataFactory.RAW_PASSWORD);

        assertThat(firstHash).isNotEqualTo(TestDataFactory.RAW_PASSWORD);
        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(passwordEncoder.matches(TestDataFactory.RAW_PASSWORD, firstHash)).isTrue();
        assertThat(passwordEncoder.matches(TestDataFactory.RAW_PASSWORD, secondHash)).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", firstHash)).isFalse();
    }

    @Test
    @DisplayName("exposes a ModelMapper")
    void shouldExposeModelMapper() {
        assertThat(appConfig.modelMapper()).isNotNull().isInstanceOf(ModelMapper.class);
    }

    @Test
    @DisplayName("the ModelMapper maps a User onto a UserResponse without leaking the password")
    void shouldMapUserOntoUserResponse() {
        ModelMapper modelMapper = appConfig.modelMapper();
        User user = TestDataFactory.user();
        user.setRole(Role.ADMIN);

        UserResponse response = modelMapper.map(user, UserResponse.class);

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(response.getLastName()).isEqualTo(user.getLastName());
        assertThat(response.getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getPhoneNumber()).isEqualTo(user.getPhoneNumber());
        assertThat(response.getRole()).isEqualTo(Role.ADMIN.name());
        assertThat(response.getCreatedAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("the ModelMapper maps the nested addresses of a User")
    void shouldMapNestedAddresses() {
        ModelMapper modelMapper = appConfig.modelMapper();
        User user = TestDataFactory.user();
        user.setAddresses(List.of(TestDataFactory.address(10L, user, true)));

        UserResponse response = modelMapper.map(user, UserResponse.class);

        assertThat(response.getAddresses()).hasSize(1);
        AddressResponse addressResponse = response.getAddresses().get(0);
        assertThat(addressResponse.getId()).isEqualTo(10L);
        assertThat(addressResponse.getCity()).isEqualTo("Bengaluru");
        assertThat(addressResponse.getPinCode()).isEqualTo("560001");
        assertThat(addressResponse.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("the ModelMapper maps an Address onto an AddressResponse")
    void shouldMapAddressOntoAddressResponse() {
        ModelMapper modelMapper = appConfig.modelMapper();
        Address address = TestDataFactory.address(10L, TestDataFactory.user(), true);

        AddressResponse response = modelMapper.map(address, AddressResponse.class);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getHouseNo()).isEqualTo("B-101");
        assertThat(response.getStreet()).isEqualTo("MG Road");
        assertThat(response.getCity()).isEqualTo("Bengaluru");
        assertThat(response.getState()).isEqualTo("Karnataka");
        assertThat(response.getCountry()).isEqualTo("India");
        assertThat(response.getPinCode()).isEqualTo("560001");
        assertThat(response.getIsDefault()).isTrue();
    }
}


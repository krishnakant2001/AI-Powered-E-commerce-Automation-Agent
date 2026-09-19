package com.strikerkk.aicommerce.user_service.support;

import com.strikerkk.aicommerce.user_service.dto.request.AddressRequest;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.LoginUserRequest;
import com.strikerkk.aicommerce.user_service.dto.request.UpdateUserDetailsRequest;
import com.strikerkk.aicommerce.user_service.dto.response.AddressResponse;
import com.strikerkk.aicommerce.user_service.dto.response.UserResponse;
import com.strikerkk.aicommerce.user_service.entity.Address;
import com.strikerkk.aicommerce.user_service.entity.User;
import com.strikerkk.aicommerce.user_service.entity.enums.Role;

import java.time.LocalDateTime;
import java.util.ArrayList;

public final class TestDataFactory {

    public static final Long USER_ID = 1L;
    public static final String USER_ID_AS_STRING = "1";
    public static final String EMAIL = "krishna@example.com";
    public static final String RAW_PASSWORD = "Str0ngPassword";
    public static final String ENCODED_PASSWORD = "$2a$10$encodedPasswordValue";
    public static final String PHONE_NUMBER = "9876543210";

    private TestDataFactory() {
    }

    // entities ------------------------------------------------------------------

    public static User user() {
        return User.builder()
                .id(USER_ID)
                .firstName("Krishnakant")
                .lastName("Nagvanshi")
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .phoneNumber(PHONE_NUMBER)
                .role(Role.USER)
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .addresses(new ArrayList<>())
                .build();
    }

    /** A brand-new user, exactly as produced by {@code UserService#registerUser} (no id yet). */
    public static User transientUser() {
        return User.builder()
                .firstName("Krishnakant")
                .lastName("Nagvanshi")
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .phoneNumber(PHONE_NUMBER)
                .addresses(new ArrayList<>())
                .build();
    }

    public static Address address(Long id, User user, boolean isDefault) {
        return Address.builder()
                .id(id)
                .user(user)
                .houseNo("B-101")
                .street("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .country("India")
                .pinCode("560001")
                .isDefault(isDefault)
                .build();
    }

    // requests ------------------------------------------------------------------

    public static CreateUserRequest createUserRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Krishnakant");
        request.setLastName("Nagvanshi");
        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        request.setPhoneNumber(PHONE_NUMBER);
        return request;
    }

    public static LoginUserRequest loginUserRequest() {
        LoginUserRequest request = new LoginUserRequest();
        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        return request;
    }

    public static UpdateUserDetailsRequest updateUserDetailsRequest() {
        UpdateUserDetailsRequest request = new UpdateUserDetailsRequest();
        request.setFirstName("Updated");
        request.setLastName("Name");
        request.setPhoneNumber("9123456780");
        return request;
    }

    public static AddressRequest addressRequest() {
        AddressRequest request = new AddressRequest();
        request.setHouseNo("B-101");
        request.setStreet("MG Road");
        request.setCity("Bengaluru");
        request.setState("Karnataka");
        request.setCountry("India");
        request.setPinCode("560001");
        return request;
    }

    // responses -----------------------------------------------------------------

    public static UserResponse userResponse() {
        UserResponse response = new UserResponse();
        response.setId(USER_ID);
        response.setFirstName("Krishnakant");
        response.setLastName("Nagvanshi");
        response.setEmail(EMAIL);
        response.setPhoneNumber(PHONE_NUMBER);
        response.setRole(Role.USER.name());
        response.setCreatedAt(LocalDateTime.now());
        return response;
    }

    public static AddressResponse addressResponse(Long id, boolean isDefault) {
        AddressResponse response = new AddressResponse();
        response.setId(id);
        response.setHouseNo("B-101");
        response.setStreet("MG Road");
        response.setCity("Bengaluru");
        response.setState("Karnataka");
        response.setCountry("India");
        response.setPinCode("560001");
        response.setIsDefault(isDefault);
        return response;
    }
}


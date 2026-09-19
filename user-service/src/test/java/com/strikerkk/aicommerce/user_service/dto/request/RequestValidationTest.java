package com.strikerkk.aicommerce.user_service.dto.request;

import com.strikerkk.aicommerce.user_service.support.TestDataFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Request DTO bean validation")
class RequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private static <T> Set<String> violatedFields(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static <T> Set<String> messages(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ------------------------------------------------------------------ CreateUserRequest

    @Nested
    @DisplayName("CreateUserRequest")
    class CreateUser {

        @Test
        @DisplayName("accepts a fully populated, valid payload")
        void shouldAcceptValidPayload() {
            assertThat(validator.validate(TestDataFactory.createUserRequest())).isEmpty();
        }

        @Test
        @DisplayName("accepts a payload without last name and without phone number")
        void shouldAcceptOptionalFieldsBeingNull() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setLastName(null);
            request.setPhoneNumber(null);

            assertThat(validator.validate(request)).isEmpty();
        }

        @ParameterizedTest(name = "firstName = \"{0}\"")
        @ValueSource(strings = {"", "   "})
        @DisplayName("rejects a blank first name")
        void shouldRejectBlankFirstName(String firstName) {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setFirstName(firstName);

            assertThat(violatedFields(request)).contains("firstName");
            assertThat(messages(request)).contains("First name is required");
        }

        @Test
        @DisplayName("rejects a null first name")
        void shouldRejectNullFirstName() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setFirstName(null);

            assertThat(violatedFields(request)).contains("firstName");
        }

        @Test
        @DisplayName("rejects a first name longer than 50 characters")
        void shouldRejectTooLongFirstName() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setFirstName("a".repeat(51));

            assertThat(violatedFields(request)).contains("firstName");
            assertThat(messages(request)).contains("First name cannot exceed 50 characters");
        }

        @Test
        @DisplayName("accepts a first name of exactly 50 characters")
        void shouldAcceptFirstNameOfExactly50Characters() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setFirstName("a".repeat(50));

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a last name longer than 50 characters")
        void shouldRejectTooLongLastName() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setLastName("b".repeat(51));

            assertThat(violatedFields(request)).contains("lastName");
        }

        @ParameterizedTest(name = "email = \"{0}\"")
        @ValueSource(strings = {"not-an-email", "missing-at.example.com", "spaces in@example.com"})
        @DisplayName("rejects a malformed email")
        void shouldRejectMalformedEmail(String email) {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setEmail(email);

            assertThat(violatedFields(request)).contains("email");
        }

        @Test
        @DisplayName("rejects a blank email")
        void shouldRejectBlankEmail() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setEmail("   ");

            assertThat(violatedFields(request)).contains("email");
            assertThat(messages(request)).contains("Email is required");
        }

        @Test
        @DisplayName("rejects a password shorter than 8 characters")
        void shouldRejectShortPassword() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setPassword("1234567");

            assertThat(violatedFields(request)).contains("password");
            assertThat(messages(request)).contains("Password must be at least 8 characters");
        }

        @Test
        @DisplayName("accepts a password of exactly 8 characters")
        void shouldAcceptPasswordOfExactly8Characters() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setPassword("12345678");

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank password")
        void shouldRejectBlankPassword() {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setPassword("        ");

            // blank but long enough for @Size, so only @NotBlank must fire
            assertThat(violatedFields(request)).contains("password");
            assertThat(messages(request)).contains("Password is required");
        }

        @ParameterizedTest(name = "phoneNumber = \"{0}\"")
        @ValueSource(strings = {"12345", "abcdefghij", "+", "98765432101234567", "98-76543210"})
        @DisplayName("rejects a malformed phone number")
        void shouldRejectMalformedPhoneNumber(String phoneNumber) {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setPhoneNumber(phoneNumber);

            assertThat(violatedFields(request)).contains("phoneNumber");
            assertThat(messages(request)).contains("Invalid phone number format");
        }

        @ParameterizedTest(name = "phoneNumber = \"{0}\"")
        @ValueSource(strings = {"9876543210", "+919876543210", "123456789012345"})
        @DisplayName("accepts a well formed phone number")
        void shouldAcceptWellFormedPhoneNumber(String phoneNumber) {
            CreateUserRequest request = TestDataFactory.createUserRequest();
            request.setPhoneNumber(phoneNumber);

            assertThat(validator.validate(request)).isEmpty();
        }
    }

    // LoginUserRequest -------------------------------------------------------------------

    @Nested
    @DisplayName("LoginUserRequest")
    class Login {

        @Test
        @DisplayName("accepts a valid payload")
        void shouldAcceptValidPayload() {
            assertThat(validator.validate(TestDataFactory.loginUserRequest())).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank email")
        void shouldRejectBlankEmail() {
            LoginUserRequest request = TestDataFactory.loginUserRequest();
            request.setEmail("");

            assertThat(violatedFields(request)).contains("email");
        }

        @Test
        @DisplayName("rejects a malformed email")
        void shouldRejectMalformedEmail() {
            LoginUserRequest request = TestDataFactory.loginUserRequest();
            request.setEmail("nope");

            assertThat(violatedFields(request)).contains("email");
            assertThat(messages(request)).contains("Invalid email format");
        }

        @Test
        @DisplayName("rejects a blank password")
        void shouldRejectBlankPassword() {
            LoginUserRequest request = TestDataFactory.loginUserRequest();
            request.setPassword("   ");

            assertThat(violatedFields(request)).contains("password");
            assertThat(messages(request)).contains("Password is required");
        }

        @Test
        @DisplayName("does not enforce any password length so that legacy accounts can still log in")
        void shouldNotEnforcePasswordLength() {
            LoginUserRequest request = TestDataFactory.loginUserRequest();
            request.setPassword("a");

            assertThat(validator.validate(request)).isEmpty();
        }
    }

    // UpdateUserDetailsRequest -----------------------------------------------------------

    @Nested
    @DisplayName("UpdateUserDetailsRequest")
    class UpdateUserDetails {

        @Test
        @DisplayName("accepts a valid payload")
        void shouldAcceptValidPayload() {
            assertThat(validator.validate(TestDataFactory.updateUserDetailsRequest())).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank first name")
        void shouldRejectBlankFirstName() {
            UpdateUserDetailsRequest request = TestDataFactory.updateUserDetailsRequest();
            request.setFirstName("  ");

            assertThat(violatedFields(request)).contains("firstName");
        }

        @Test
        @DisplayName("requires the phone number, unlike the sign up payload")
        void shouldRequirePhoneNumber() {
            UpdateUserDetailsRequest request = TestDataFactory.updateUserDetailsRequest();
            request.setPhoneNumber(null);

            assertThat(violatedFields(request)).contains("phoneNumber");
            assertThat(messages(request)).contains("Phone number required");
        }

        @Test
        @DisplayName("rejects a malformed phone number")
        void shouldRejectMalformedPhoneNumber() {
            UpdateUserDetailsRequest request = TestDataFactory.updateUserDetailsRequest();
            request.setPhoneNumber("12345");

            assertThat(violatedFields(request)).contains("phoneNumber");
            assertThat(messages(request)).contains("Invalid phone number format");
        }

        @Test
        @DisplayName("accepts a null last name")
        void shouldAcceptNullLastName() {
            UpdateUserDetailsRequest request = TestDataFactory.updateUserDetailsRequest();
            request.setLastName(null);

            assertThat(validator.validate(request)).isEmpty();
        }
    }

    // AddressRequest ---------------------------------------------------------------------

    @Nested
    @DisplayName("AddressRequest")
    class Address {

        @Test
        @DisplayName("accepts a valid payload")
        void shouldAcceptValidPayload() {
            assertThat(validator.validate(TestDataFactory.addressRequest())).isEmpty();
        }

        @Test
        @DisplayName("accepts a payload without house number and without the isDefault flag")
        void shouldAcceptOptionalFieldsBeingNull() {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setHouseNo(null);
            request.setIsDefault(null);

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank street")
        void shouldRejectBlankStreet() {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setStreet(" ");

            assertThat(violatedFields(request)).contains("street");
            assertThat(messages(request)).contains("Street Address name is required");
        }

        @Test
        @DisplayName("rejects a blank city")
        void shouldRejectBlankCity() {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setCity(null);

            assertThat(violatedFields(request)).contains("city");
            assertThat(messages(request)).contains("City name is required");
        }

        @Test
        @DisplayName("rejects a blank state")
        void shouldRejectBlankState() {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setState(null);

            assertThat(violatedFields(request)).contains("state");
            assertThat(messages(request)).contains("State name is required");
        }

        @Test
        @DisplayName("rejects a blank country")
        void shouldRejectBlankCountry() {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setCountry(null);

            assertThat(violatedFields(request)).contains("country");
            assertThat(messages(request)).contains("Country name is required");
        }

        @ParameterizedTest(name = "pinCode = \"{0}\"")
        @ValueSource(strings = {"012345", "12345", "1234567", "ABC123", "56 001"})
        @DisplayName("rejects a malformed pin code")
        void shouldRejectMalformedPinCode(String pinCode) {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setPinCode(pinCode);

            assertThat(violatedFields(request)).contains("pinCode");
            assertThat(messages(request)).contains("Pin-code must be at least 6 digits");
        }

        @ParameterizedTest(name = "pinCode = \"{0}\"")
        @ValueSource(strings = {"560001", "110001", "999999"})
        @DisplayName("accepts a well formed Indian pin code")
        void shouldAcceptWellFormedPinCode(String pinCode) {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setPinCode(pinCode);

            assertThat(validator.validate(request)).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank pin code")
        void shouldRejectBlankPinCode() {
            AddressRequest request = TestDataFactory.addressRequest();
            request.setPinCode("");

            assertThat(violatedFields(request)).contains("pinCode");
            assertThat(messages(request)).contains("Pin-code is required");
        }
    }
}


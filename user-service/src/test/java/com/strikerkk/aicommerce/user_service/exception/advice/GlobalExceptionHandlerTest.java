package com.strikerkk.aicommerce.user_service.exception.advice;

import com.strikerkk.aicommerce.user_service.common.ApiResponse;
import com.strikerkk.aicommerce.user_service.common.ErrorResponse;
import com.strikerkk.aicommerce.user_service.dto.request.CreateUserRequest;
import com.strikerkk.aicommerce.user_service.exception.BadRequestException;
import com.strikerkk.aicommerce.user_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.user_service.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    /** Only used to build a valid {@link MethodParameter} for the validation exception. */
    @SuppressWarnings("unused")
    private void handlerMethodSignature(CreateUserRequest request) {
        // intentionally empty
    }

    private MethodArgumentNotValidException validationException(FieldError... fieldErrors) throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new CreateUserRequest(), "createUserRequest");
        for (FieldError fieldError : fieldErrors) {
            bindingResult.addError(fieldError);
        }
        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("handlerMethodSignature", CreateUserRequest.class),
                0);
        return new MethodArgumentNotValidException(methodParameter, bindingResult);
    }

    @Test
    @DisplayName("validation errors are reported as 400 with the first field error")
    void shouldHandleValidationErrors() throws Exception {
        MethodArgumentNotValidException exception = validationException(
                new FieldError("createUserRequest", "email", "Email is required"),
                new FieldError("createUserRequest", "password", "Password must be at least 8 characters"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("email: Email is required");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("validation errors fall back to a generic message when there is no field error")
    void shouldFallBackWhenThereIsNoFieldError() throws Exception {
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(validationException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
    }

    @Test
    @DisplayName("BadRequestException is reported as 400")
    void shouldHandleBadRequestException() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequest(new BadRequestException("User with email a@b.com is already registered"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage())
                .isEqualTo("User with email a@b.com is already registered");
    }

    @Test
    @DisplayName("ResourceNotFoundException is reported as 404")
    void shouldHandleResourceNotFoundException() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new ResourceNotFoundException("Address not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Address not found");
    }

    @Test
    @DisplayName("UnauthorizedException is reported as 403")
    void shouldHandleUnauthorizedException() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnauthorized(new UnauthorizedException("Not allowed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Not allowed");
    }

    @Test
    @DisplayName("BadCredentialsException is reported as 401 with a neutral message")
    void shouldHandleBadCredentialsException() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");

        ResponseEntity<ErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("Bad credentials"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getError()).isEqualTo("Invalid email or password");
        assertThat(response.getBody().getPath()).isEqualTo("/auth/login");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("DataIntegrityViolationException is reported as 409")
    void shouldHandleDataIntegrityViolationException() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage())
                .isEqualTo("duplicate key, Request conflicts with existing data");
    }

    @Test
    @DisplayName("any other RuntimeException is reported as 500 keeping its message")
    void shouldHandleRuntimeException() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneral(new RuntimeException("User not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("User not found");
    }

    @Test
    @DisplayName("a RuntimeException with a blank message falls back to a generic message")
    void shouldFallBackForBlankRuntimeExceptionMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(new RuntimeException("   "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong!");
    }
}


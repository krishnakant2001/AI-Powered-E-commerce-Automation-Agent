package com.strikerkk.aicommerce.cart_service.exception.advice;

import com.strikerkk.aicommerce.cart_service.common.ApiResponse;
import com.strikerkk.aicommerce.cart_service.dto.request.AddCartItemRequest;
import com.strikerkk.aicommerce.cart_service.exception.AccessDeniedException;
import com.strikerkk.aicommerce.cart_service.exception.BadRequestException;
import com.strikerkk.aicommerce.cart_service.exception.IllegalStateException;
import com.strikerkk.aicommerce.cart_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.cart_service.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void controllerMethod(AddCartItemRequest request) {
    }

    private MethodArgumentNotValidException validationFailure(FieldError... errors) throws Exception {
        Method method = GlobalExceptionHandlerTest.class
                .getDeclaredMethod("controllerMethod", AddCartItemRequest.class);
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new AddCartItemRequest(), "addCartItemRequest");
        for (FieldError error : errors) {
            bindingResult.addError(error);
        }
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    private FieldError fieldError(String field, String message) {
        return new FieldError("addCartItemRequest", field, message);
    }

    @Test
    @DisplayName("is a @RestControllerAdvice, so it applies to every controller")
    void isARestControllerAdvice() {
        assertThat(GlobalExceptionHandler.class.getAnnotation(RestControllerAdvice.class)).isNotNull();
    }

    // 400 - validation ------------------------------------------------------------------

    @Nested
    @DisplayName("MethodArgumentNotValidException -> 400")
    class Validation {

        @Test
        @DisplayName("renders the first field error as '<field>: <message>'")
        void rendersTheFirstFieldError() throws Exception {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleValidationErrors(validationFailure(fieldError("quantity", "Quantity must be at least 1")));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("quantity: Quantity must be at least 1");
            assertThat(response.getBody().getData()).isNull();
        }

        @Test
        @DisplayName("only the first error is reported when several fields are invalid")
        void reportsOnlyTheFirstError() throws Exception {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(validationFailure(
                    fieldError("productId", "Product ID is required"),
                    fieldError("variantId", "Variant ID is required")));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("productId: Product ID is required");
        }

        @Test
        @DisplayName("falls back to 'Validation failed' when there is no field error")
        void fallsBackWhenThereIsNoFieldError() throws Exception {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(validationFailure());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        }
    }

    // the mapped custom exceptions ------------------------------------------------------------------

    @Test
    @DisplayName("AccessDeniedException -> 403")
    void accessDeniedIsForbidden() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("Unauthorized request"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized request");
    }

    @Test
    @DisplayName("BadRequestException -> 400")
    void badRequestIsBadRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequest(new BadRequestException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    @DisplayName("IllegalStateException -> 400 (this is the out-of-stock answer)")
    void illegalStateIsBadRequest() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalState(
                new IllegalStateException("Product variant is out of stock. productId=1, variantId=2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("Product variant is out of stock. productId=1, variantId=2");
    }

    @Test
    @DisplayName("ResourceNotFoundException -> 404")
    void resourceNotFoundIsNotFound() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new ResourceNotFoundException("Cart Item is not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Cart Item is not found");
    }

    @Test
    @DisplayName("UnauthorizedException -> 403, not 401")
    void unauthorizedIsForbidden() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnauthorized(new UnauthorizedException("no token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("no token");
    }

    // 500 - catch all ------------------------------------------------------------------

    @Nested
    @DisplayName("RuntimeException -> 500")
    class CatchAll {

        @Test
        @DisplayName("renders the message of the failure")
        void rendersTheMessage() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new RuntimeException("Product Service unavailable"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("Product Service unavailable");
        }

        @Test
        @DisplayName("an empty message falls back to 'Something went wrong!'")
        void fallsBackOnAnEmptyMessage() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(new RuntimeException(""));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong!");
        }

        @Test
        @DisplayName("a null message makes the handler itself blow up (documents the bug)")
        void blowsUpOnANullMessage() {
            assertThatThrownBy(() -> handler.handleGeneral(new RuntimeException()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("the NumberFormatException of an anonymous caller lands here as a 500")
        void anAnonymousCallerLandsHere() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new NumberFormatException("Cannot parse null string: null"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        /** The custom AccessDeniedException is a RuntimeException, but the specific handler wins. */
        @Test
        @DisplayName("the specific handlers take precedence over the catch all")
        void theSpecificHandlersWin() {
            assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(handler.handleNotFound(new ResourceNotFoundException("x")).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("every answer keeps the ApiResponse envelope with success=false and no data")
    void everyAnswerUsesTheEnvelope() {
        for (ResponseEntity<ApiResponse<Void>> response : java.util.List.of(
                handler.handleAccessDenied(new AccessDeniedException("a")),
                handler.handleBadRequest(new BadRequestException("b")),
                handler.handleIllegalState(new IllegalStateException("c")),
                handler.handleNotFound(new ResourceNotFoundException("d")),
                handler.handleUnauthorized(new UnauthorizedException("e")),
                handler.handleGeneral(new RuntimeException("f")))) {

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getData()).isNull();
            assertThat(response.getBody().getTimestamp()).isNotNull();
        }
    }
}


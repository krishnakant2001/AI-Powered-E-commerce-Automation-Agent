package com.strikerkk.aicommerce.product_service.exception.advice;

import com.strikerkk.aicommerce.product_service.common.ApiResponse;
import com.strikerkk.aicommerce.product_service.dto.request.ProductRequest;
import com.strikerkk.aicommerce.product_service.exception.BadRequestException;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Only used to build a realistic {@link MethodParameter}. */
    @SuppressWarnings("unused")
    private void dummyEndpoint(ProductRequest request) {
    }

    private MethodArgumentNotValidException validationException(String... fieldAndMessage)
            throws NoSuchMethodException {

        Method method = getClass().getDeclaredMethod("dummyEndpoint", ProductRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        BindingResult bindingResult =
                new BeanPropertyBindingResult(new ProductRequest(), "productRequest");
        for (int i = 0; i < fieldAndMessage.length; i += 2) {
            bindingResult.rejectValue(fieldAndMessage[i], "Invalid", fieldAndMessage[i + 1]);
        }

        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    // validation errors -------------------------------------------------------------

    @Nested
    @DisplayName("MethodArgumentNotValidException")
    class ValidationErrors {

        @Test
        @DisplayName("returns 400 with 'field: message'")
        void shouldReturn400() throws Exception {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleValidationErrors(validationException("name", "Product name is required"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("name: Product name is required");
            assertThat(response.getBody().getData()).isNull();
            assertThat(response.getBody().getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("reports only the first field error")
        void shouldReportOnlyTheFirstError() throws Exception {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(
                    validationException(
                            "name", "Product name is required",
                            "brand", "Brand is required"));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("name: Product name is required");
        }

        @Test
        @DisplayName("falls back to a generic message when there is no field error")
        void shouldFallBackToAGenericMessage() throws Exception {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(validationException());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        }
    }

    // BadRequest ------------------------------------------------------------------

    @Nested
    @DisplayName("BadRequestException")
    class BadRequest {

        @Test
        @DisplayName("returns 400 with the message of the exception")
        void shouldReturn400() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleBadRequest(new BadRequestException("Invalid payload"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid payload");
        }
    }

    // ResourceNotFound ------------------------------------------------------------

    @Nested
    @DisplayName("ResourceNotFoundException")
    class NotFound {

        @Test
        @DisplayName("returns 404 with the message of the exception")
        void shouldReturn404() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleNotFound(new ResourceNotFoundException("Product not found"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("Product not found");
            assertThat(response.getBody().getData()).isNull();
        }

        @Test
        @DisplayName("keeps the message of every not-found flavour of the module")
        void shouldKeepEveryNotFoundMessage() {
            assertThat(handler.handleNotFound(new ResourceNotFoundException("Product is not found"))
                    .getBody()).isNotNull()
                    .extracting(ApiResponse::getMessage).isEqualTo("Product is not found");

            assertThat(handler.handleNotFound(new ResourceNotFoundException("Variant not found for this product"))
                    .getBody()).isNotNull()
                    .extracting(ApiResponse::getMessage).isEqualTo("Variant not found for this product");

            assertThat(handler.handleNotFound(new ResourceNotFoundException("Image not found for this product"))
                    .getBody()).isNotNull()
                    .extracting(ApiResponse::getMessage).isEqualTo("Image not found for this product");

            assertThat(handler.handleNotFound(new ResourceNotFoundException("Product variant is not found"))
                    .getBody()).isNotNull()
                    .extracting(ApiResponse::getMessage).isEqualTo("Product variant is not found");
        }
    }

    // Unauthorized ---------------------------------------------------------------

    @Nested
    @DisplayName("UnauthorizedException")
    class Unauthorized {

        @Test
        @DisplayName("returns 403 - the caller is authenticated but does not own the product")
        void shouldReturn403() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorized(
                    new UnauthorizedException("You are not authorised to modify this product"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage())
                    .isEqualTo("You are not authorised to modify this product");
        }
    }

    // catch all -------------------------------------------------------------------

    @Nested
    @DisplayName("RuntimeException (catch all)")
    class CatchAll {

        @Test
        @DisplayName("returns 500 with the message of the exception")
        void shouldReturn500() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new IllegalStateException("S3 unavailable"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("S3 unavailable");
        }

        @Test
        @DisplayName("falls back to a generic message for an empty message")
        void shouldFallBackForAnEmptyMessage() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new RuntimeException(""));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong!");
        }

        @Test
        @DisplayName("also catches the S3 upload failure wrapped by S3ImageService")
        void shouldCatchTheWrappedUploadFailure() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(
                    new RuntimeException("Failed to upload a file on S3 bucket", new java.io.IOException()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Failed to upload a file on S3 bucket");
        }

        @Test
        @DisplayName("blows up on a RuntimeException that carries no message")
        void shouldBlowUpOnANullMessage() {
            assertThatThrownBy(() -> handler.handleGeneral(new RuntimeException()))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}


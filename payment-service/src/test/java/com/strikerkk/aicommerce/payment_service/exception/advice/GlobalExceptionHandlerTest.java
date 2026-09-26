package com.strikerkk.aicommerce.payment_service.exception.advice;

import com.strikerkk.aicommerce.payment_service.common.ApiResponse;
import com.strikerkk.aicommerce.payment_service.controller.PaymentController;
import com.strikerkk.aicommerce.payment_service.dto.request.InitiatePaymentRequest;
import com.strikerkk.aicommerce.payment_service.exception.AccessDeniedException;
import com.strikerkk.aicommerce.payment_service.exception.BadRequestException;
import com.strikerkk.aicommerce.payment_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.payment_service.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MethodArgumentNotValidException validationError(FieldError... errors) throws Exception {
        Method method = PaymentController.class.getMethod("initiatePayment", InitiatePaymentRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        BindingResult bindingResult =
                new BeanPropertyBindingResult(new InitiatePaymentRequest(), "initiatePaymentRequest");
        Arrays.stream(errors).forEach(bindingResult::addError);

        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    // ==================================================================
    // validation
    // ==================================================================

    @Nested
    @DisplayName("a validation error")
    class Validation {

        @Test
        @DisplayName("becomes a 400 naming the offending field")
        void becomesA400() throws Exception {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(
                    validationError(new FieldError("initiatePaymentRequest", "orderId", "Order ID is required")));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("orderId: Order ID is required");
        }

        @Test
        @DisplayName("reports only the first field, the client fixes them one by one")
        void reportsOnlyTheFirstField() throws Exception {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(
                    validationError(
                            new FieldError("initiatePaymentRequest", "orderId", "Order ID is required"),
                            new FieldError("initiatePaymentRequest", "gateway", "Gateway is required")));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("orderId: Order ID is required");
        }

        @Test
        @DisplayName("falls back to a generic message when no field is at fault")
        void fallsBackToAGenericMessage() throws Exception {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(validationError());

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        }

        @Test
        @DisplayName("never carries data")
        void neverCarriesData() throws Exception {
            ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(
                    validationError(new FieldError("initiatePaymentRequest", "orderId", "required")));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).isNull();
        }
    }

    // ==================================================================
    // the business failures
    // ==================================================================

    @Nested
    @DisplayName("the business failures")
    class BusinessFailures {

        @Test
        @DisplayName("a missing payment becomes a 404")
        void aMissingPaymentBecomesA404() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleNotFound(new ResourceNotFoundException("Payment not found"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("Payment not found");
            assertThat(response.getBody().getData()).isNull();
        }

        @Test
        @DisplayName("a bad request becomes a 400")
        void aBadRequestBecomesA400() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleBadRequest(new BadRequestException("bad input"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("bad input");
        }

        @Test
        @DisplayName("a denied access becomes a 403")
        void aDeniedAccessBecomesA403() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleAccessDenied(new AccessDeniedException("This payment is not yours"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("This payment is not yours");
        }

        @Test
        @DisplayName("paying somebody else's order becomes a 403")
        void payingSomebodyElsesOrderBecomesA403() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleUnauthorized(new UnauthorizedException("Order does not belong to this user"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Order does not belong to this user");
        }

        @Test
        @DisplayName("an illegal state becomes a 400")
        void anIllegalStateBecomesA400() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleIllegalState(new IllegalStateException("wrong state"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("wrong state");
        }

        @Test
        @DisplayName("the illegal state handler binds java.lang.IllegalStateException, not the custom one")
        void theIllegalStateHandlerBindsTheJdkType() throws Exception {
            Method handlerMethod = GlobalExceptionHandler.class.getDeclaredMethod(
                    "handleIllegalState", java.lang.IllegalStateException.class);

            assertThat(handlerMethod.getAnnotation(ExceptionHandler.class).value())
                    .containsExactly(java.lang.IllegalStateException.class);

            // The custom one is a plain RuntimeException, so it ends up in the catch all - a 500.
            assertThat(java.lang.IllegalStateException.class.isAssignableFrom(
                    com.strikerkk.aicommerce.payment_service.exception.IllegalStateException.class))
                    .isFalse();
        }
    }

    // ==================================================================
    // the catch all
    // ==================================================================

    @Nested
    @DisplayName("the catch all")
    class CatchAll {

        @Test
        @DisplayName("turns a double payment into a 500 carrying the reason")
        void turnsADoublePaymentIntoA500() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new RuntimeException("Payment already exists for this order"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).isEqualTo("Payment already exists for this order");
        }

        @Test
        @DisplayName("reports an unreachable order-service")
        void reportsAnUnreachableOrderService() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new RuntimeException("Order service unavailable"));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Order service unavailable");
        }

        @Test
        @DisplayName("reports a forged webhook")
        void reportsAForgedWebhook() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new RuntimeException("Invalid webhook signature"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Invalid webhook signature");
        }

        @Test
        @DisplayName("also catches the custom IllegalStateException")
        void alsoCatchesTheCustomIllegalState() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(
                    new com.strikerkk.aicommerce.payment_service.exception.IllegalStateException("wrong state"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("wrong state");
        }

        @Test
        @DisplayName("falls back to a generic message for a blank one")
        void fallsBackForABlankMessage() {
            ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(new RuntimeException(""));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong!");
        }

        @Test
        @DisplayName("a null message makes the handler itself blow up (documents the bug)")
        void aNullMessageBlowsTheHandlerUp() {
            assertThatThrownBy(() -> handler.handleGeneral(new RuntimeException()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("a missing gateway header ends up here as a NumberFormatException")
        void aMissingHeaderEndsUpHere() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new NumberFormatException("Cannot parse null string: null"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("Cannot parse null string: null");
        }

        @Test
        @DisplayName("swallows the 4xx family of Spring MVC too (documents the bug)")
        void swallowsTheSpringMvc4xxFamily() {
            // Every Spring MVC binding failure is a RuntimeException, so a caller that sends a
            // malformed body is told 500 instead of 400.
            assertThat(RuntimeException.class).isAssignableFrom(
                    org.springframework.http.converter.HttpMessageNotReadableException.class);
            assertThat(RuntimeException.class).isAssignableFrom(
                    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);
        }
    }

    // ==================================================================
    // wiring
    // ==================================================================

    @Nested
    @DisplayName("wiring")
    class Wiring {

        @Test
        @DisplayName("is a REST controller advice, so it covers the payment and the webhook controller")
        void isARestControllerAdvice() {
            assertThat(GlobalExceptionHandler.class.getAnnotation(RestControllerAdvice.class)).isNotNull();
        }

        @Test
        @DisplayName("every handler is annotated and answers with the shared envelope")
        void everyHandlerIsAnnotated() {
            assertThat(GlobalExceptionHandler.class.getDeclaredMethods())
                    .filteredOn(m -> !m.isSynthetic())
                    .allSatisfy(method -> {
                        assertThat(method.getAnnotation(ExceptionHandler.class))
                                .as(method.getName()).isNotNull();
                        assertThat(method.getReturnType()).isEqualTo(ResponseEntity.class);
                    });
        }

        @Test
        @DisplayName("handles exactly the seven cases the service can produce")
        void handlesExactlySevenCases() {
            assertThat(GlobalExceptionHandler.class.getDeclaredMethods())
                    .filteredOn(m -> !m.isSynthetic())
                    .extracting(Method::getName)
                    .containsExactlyInAnyOrder(
                            "handleValidationErrors", "handleAccessDenied", "handleBadRequest",
                            "handleIllegalState", "handleNotFound", "handleUnauthorized", "handleGeneral");
        }

        @Test
        @DisplayName("no handler ever leaks a stack trace into the body")
        void noHandlerLeaksAStackTrace() {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleGeneral(new RuntimeException("Failed to create Payment with Razorpay: boom"));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).doesNotContain("at com.strikerkk");
        }
    }
}


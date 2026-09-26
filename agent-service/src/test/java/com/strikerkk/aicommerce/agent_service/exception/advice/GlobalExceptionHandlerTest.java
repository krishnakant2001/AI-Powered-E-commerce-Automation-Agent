package com.strikerkk.aicommerce.agent_service.exception.advice;

import com.strikerkk.aicommerce.agent_service.common.ApiResponse;
import com.strikerkk.aicommerce.agent_service.dto.request.ChatRequest;
import com.strikerkk.aicommerce.agent_service.exception.AgentException;
import com.strikerkk.aicommerce.agent_service.exception.BadRequestException;
import com.strikerkk.aicommerce.agent_service.exception.SessionNotFoundException;
import com.strikerkk.aicommerce.agent_service.exception.ToolCallException;
import com.strikerkk.aicommerce.agent_service.exception.UnauthorizedSessionAccessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void dummyEndpoint(ChatRequest request) {
        // only exists so a MethodParameter can be built
    }

    private MethodArgumentNotValidException validationFailure(String... fieldErrors) throws Exception {
        ChatRequest target = new ChatRequest();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "chatRequest");

        Arrays.stream(fieldErrors)
                .forEach(message -> bindingResult.rejectValue("message", "NotBlank", message));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", ChatRequest.class);

        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    // wiring ------------------------------------------------------------------

    @Test
    @DisplayName("is a @RestControllerAdvice")
    void isARestControllerAdvice() {
        assertThat(GlobalExceptionHandler.class.getAnnotation(RestControllerAdvice.class)).isNotNull();
    }

    @Test
    @DisplayName("every handler method is annotated with @ExceptionHandler")
    void everyHandlerIsAnnotated() {
        assertThat(GlobalExceptionHandler.class.getDeclaredMethods())
                .filteredOn(method -> !method.isSynthetic())
                .allMatch(method -> method.getAnnotation(ExceptionHandler.class) != null);
    }

    @Test
    @DisplayName("declares a handler for each exception the service throws")
    void declaresAHandlerPerException() {
        assertThat(Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(ExceptionHandler.class) != null)
                .flatMap(method -> Arrays.stream(method.getAnnotation(ExceptionHandler.class).value())))
                .contains(MethodArgumentNotValidException.class,
                        SessionNotFoundException.class,
                        UnauthorizedSessionAccessException.class,
                        AgentException.class,
                        ToolCallException.class,
                        BadRequestException.class,
                        Exception.class);
    }

    // behaviour ------------------------------------------------------------------

    @Test
    @DisplayName("a validation failure becomes 400 with the first field error")
    void validationFailureBecomes400() throws Exception {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleValidationErrors(validationFailure("Message cannot be blank"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("message: Message cannot be blank");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    @DisplayName("only the first field error is reported")
    void onlyTheFirstFieldErrorIsReported() throws Exception {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleValidationErrors(validationFailure("first problem", "second problem"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("message: first problem");
    }

    @Test
    @DisplayName("falls back to a generic message when there is no field error")
    void fallsBackWhenThereIsNoFieldError() throws Exception {
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationErrors(validationFailure());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
    }

    @Test
    @DisplayName("an unknown session becomes 404")
    void unknownSessionBecomes404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleSessionNotFound(new SessionNotFoundException("abc-123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Session not found: abc-123");
    }

    @Test
    @DisplayName("touching somebody else's session becomes 403")
    void unauthorizedAccessBecomes403() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnauthorizedAccess(new UnauthorizedSessionAccessException("abc-123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("You are not authorized to access session: abc-123");
    }

    @Test
    @DisplayName("an LLM failure becomes 500 and keeps the reason")
    void agentExceptionBecomes500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAgentException(new AgentException("LLM api error: 529"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("LLM api error: 529");
    }

    @Test
    @DisplayName("a downstream failure becomes 502 with a prefixed reason")
    void toolCallExceptionBecomes502() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleToolCallException(new ToolCallException("addToCart", "cart-service is down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Service call failed: cart-service is down");
    }

    @Test
    @DisplayName("a bad request becomes 400")
    void badRequestBecomes400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequestException(new BadRequestException("sessionId is required"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("sessionId is required");
    }

    @Test
    @DisplayName("anything else becomes a 500 that leaks nothing")
    void anythingElseBecomesASafe500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGenericException(new IllegalStateException("jdbc url password=hunter2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong, Please try again.");
        assertThat(response.getBody().getMessage()).doesNotContain("hunter2");
    }

    @Test
    @DisplayName("every response is stamped and carries no data")
    void everyResponseIsStamped() {
        assertThat(handler.handleAgentException(new AgentException("x")).getBody())
                .satisfies(body -> {
                    assertThat(body).isNotNull();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getData()).isNull();
                });
    }
}


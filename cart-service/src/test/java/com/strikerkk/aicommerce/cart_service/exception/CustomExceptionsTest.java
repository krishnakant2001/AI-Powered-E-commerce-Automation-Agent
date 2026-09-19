package com.strikerkk.aicommerce.cart_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Custom exceptions")
class CustomExceptionsTest {

    @Test
    @DisplayName("AccessDeniedException carries its message")
    void accessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Unauthorized request");

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Unauthorized request");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("BadRequestException carries its message")
    void badRequest() {
        BadRequestException ex = new BadRequestException("Quantity must be at least 1");

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Quantity must be at least 1");
    }

    @Test
    @DisplayName("IllegalStateException carries its message")
    void illegalState() {
        IllegalStateException ex = new IllegalStateException("Product variant is out of stock");

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Product variant is out of stock");
    }

    @Test
    @DisplayName("IllegalStateException is the custom one, NOT java.lang.IllegalStateException")
    void illegalStateShadowsTheJdkType() {
        IllegalStateException ex = new IllegalStateException("boom");

        assertThat(ex).isNotInstanceOf(java.lang.IllegalStateException.class);
        assertThat(IllegalStateException.class.getName())
                .isEqualTo("com.strikerkk.aicommerce.cart_service.exception.IllegalStateException");
    }

    @Test
    @DisplayName("ResourceNotFoundException carries its message")
    void resourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Cart item not found 500");

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("Cart item not found 500");
    }

    @Test
    @DisplayName("UnauthorizedException carries its message")
    void unauthorized() {
        UnauthorizedException ex = new UnauthorizedException("nope");

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("nope");
    }

    @Test
    @DisplayName("every one of them is unchecked, so no method has to declare it")
    void allAreUnchecked() {
        assertThat(RuntimeException.class)
                .isAssignableFrom(AccessDeniedException.class)
                .isAssignableFrom(BadRequestException.class)
                .isAssignableFrom(IllegalStateException.class)
                .isAssignableFrom(ResourceNotFoundException.class)
                .isAssignableFrom(UnauthorizedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a very long message that should survive untouched"})
    @DisplayName("any message is stored verbatim")
    void anyMessageIsStoredVerbatim(String message) {
        assertThat(new ResourceNotFoundException(message).getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("a null message is allowed by the constructor")
    void aNullMessageIsAllowed() {
        assertThat(new BadRequestException(null).getMessage()).isNull();
    }

    @Test
    @DisplayName("they are throwable and catchable as RuntimeException")
    void areThrowable() {
        assertThatThrownBy(() -> {
            throw new AccessDeniedException("Unauthorized request");
        })
                .isInstanceOf(RuntimeException.class)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Unauthorized request");
    }
}


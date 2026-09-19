package com.strikerkk.aicommerce.product_service.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Custom exceptions")
class CustomExceptionsTest {

    @Test
    @DisplayName("ResourceNotFoundException carries its message")
    void resourceNotFoundExceptionCarriesItsMessage() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Product not found");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("Product not found");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("BadRequestException carries its message")
    void badRequestExceptionCarriesItsMessage() {
        BadRequestException exception = new BadRequestException("Invalid payload");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo("Invalid payload");
    }

    @Test
    @DisplayName("UnauthorizedException carries its message")
    void unauthorizedExceptionCarriesItsMessage() {
        UnauthorizedException exception =
                new UnauthorizedException("You are not authorised to modify this product");

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage())
                .isEqualTo("You are not authorised to modify this product");
    }

    @Test
    @DisplayName("every custom exception is unchecked, so no method has to declare it")
    void everyCustomExceptionIsUnchecked() {
        assertThat(RuntimeException.class)
                .isAssignableFrom(ResourceNotFoundException.class)
                .isAssignableFrom(BadRequestException.class)
                .isAssignableFrom(UnauthorizedException.class);
    }

    @Test
    @DisplayName("they are three distinct types, so the advice can map three status codes")
    void theyAreThreeDistinctTypes() {
        assertThat(ResourceNotFoundException.class).isNotEqualTo(BadRequestException.class);
        assertThat(BadRequestException.class).isNotEqualTo(UnauthorizedException.class);
        assertThat(UnauthorizedException.class).isNotEqualTo(ResourceNotFoundException.class);

        assertThatThrownBy(() -> {
            throw new ResourceNotFoundException("nope");
        })
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(UnauthorizedException.class)
                .isNotInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a null message stays null")
    void aNullMessageStaysNull() {
        assertThat(new ResourceNotFoundException(null).getMessage()).isNull();
    }
}

